/**
 * payment-load-test.js
 *
 * 결제 시스템 병목 분석용 단일 파일 부하테스트
 * 외부 의존성 없음. 이 파일 하나로 모든 시나리오 실행 가능.
 *
 * ────────────────────────────────────────────────────────────
 * 실행 예시 (EC2 or 로컬)
 * ────────────────────────────────────────────────────────────
 *
 * # 1단계: 스모크  (기본 동작 확인, 5 VU)
 * k6 run -e BASE_URL=http://localhost:8080 \
 *        -e USER_IDS=1,2,3 \
 *        -e SKU_IDS=101,102 \
 *        -e PROFILE=smoke \
 *        k6/payment-load-test.js
 *
 * # 2단계: 기준선 (30 VU, 5분)
 * k6 run -e BASE_URL=http://localhost:8080 \
 *        -e USER_IDS=1,2,3,4,5 \
 *        -e SKU_IDS=101,102,103 \
 *        -e PROFILE=baseline \
 *        k6/payment-load-test.js
 *
 * # 3단계: 부하   (최대 100 VU - 병목 식별)
 * k6 run -e BASE_URL=http://localhost:8080 \
 *        -e USER_IDS=1,2,3,4,5 \
 *        -e SKU_IDS=101,102,103 \
 *        -e PROFILE=load \
 *        k6/payment-load-test.js
 *
 * # 4단계: 스파이크 (커넥션 풀 & Redis 락 한계 측정)
 * k6 run -e BASE_URL=http://localhost:8080 \
 *        -e USER_IDS=1,2,3,4,5 \
 *        -e SKU_IDS=101,102,103 \
 *        -e PROFILE=spike \
 *        k6/payment-load-test.js
 *
 * # 5단계: 중복결제 동시성 테스트 (멱등성 & 분산 락 검증)
 * k6 run -e BASE_URL=http://localhost:8080 \
 *        -e USER_IDS=1,2,3,4,5 \
 *        -e SKU_IDS=101,102,103 \
 *        -e PROFILE=concurrency \
 *        k6/payment-load-test.js
 *
 * ────────────────────────────────────────────────────────────
 * 주요 환경변수
 * ────────────────────────────────────────────────────────────
 *   BASE_URL        앱 서버 주소                 (기본: http://localhost:8080)
 *   USER_IDS        테스트용 userId 쉼표 구분     (기본: 1,2,3)
 *   SKU_IDS         테스트용 skuId 쉼표 구분      (기본: 101,102)
 *   PROFILE         테스트 프로파일               (기본: baseline)
 *   THINK_TIME      요청 간 대기 시간(초)          (기본: 0 = 최대 TPS 측정)
 *
 * ────────────────────────────────────────────────────────────
 * 병목 분석 포인트 (코드 기반)
 * ────────────────────────────────────────────────────────────
 *   [1] Redis 멱등성 키 획득   → payment:idempotency:{paymentKey} SETNX
 *   [2] Redis Order 분산 락   → payment:lock:order:{orderId} SETNX
 *   [3] DB SELECT FOR UPDATE  → findByOrderNumberWithItemsForUpdate
 *   [4] DB INSERT/UPDATE      → payment save + order save
 *   [5] DB DELETE             → cartJPARepository.deleteAllByUserIdAndIdIn
 *   [6] HikariCP 커넥션 풀     → DB 커넥션 대기로 인한 500/타임아웃
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ─── 커스텀 메트릭 ────────────────────────────────────────────────────────────

// 단계별 응답 시간 (Trend: p50/p95/p99 자동 산출)
const orderCreateMs = new Trend('step1_order_create_ms', true);
const paymentConfirmMs = new Trend('step2_payment_confirm_ms', true);

// 병목 원인별 카운터
const errOrderCreate = new Counter('err_order_create');
const errPaymentConfirm = new Counter('err_payment_confirm');
const errRedisLock = new Counter('err_redis_lock_fail');       // 409 → 분산 락 실패
const errIdempotency = new Counter('err_idempotency_blocked'); // 이미 처리 중 차단
const errHikariPool = new Counter('err_hikari_pool');          // 커넥션 풀 고갈 의심
const errDuplicateBlocked = new Counter('err_duplicate_ok');   // 중복 결제 정상 차단 (concurrency 시나리오)

// 성공률
const ratePaymentSuccess = new Rate('rate_payment_success');
const rateE2eSuccess = new Rate('rate_e2e_success');

// ─── 환경 변수 ────────────────────────────────────────────────────────────────

const BASE_URL = String(__ENV.BASE_URL || 'http://localhost:8080');
const PROFILE = String(__ENV.PROFILE || 'baseline').toLowerCase();
const THINK_TIME = Number(__ENV.THINK_TIME || '0');

// userId, skuId 는 쉼표로 전달: -e USER_IDS=1,2,3
const USER_IDS = parseIds(__ENV.USER_IDS, [1, 2, 3]);
const SKU_IDS = parseIds(__ENV.SKU_IDS, [101, 102]);

// concurrency 시나리오 전용: 동일 주문에 몰아칠 동시 VU 수
const DUPLICATE_VUS = Number(__ENV.DUPLICATE_VUS || '20');

// ─── 부하 프로파일 ────────────────────────────────────────────────────────────

/**
 * 각 프로파일의 의도:
 *
 * smoke      → 에러 없이 기본 흐름 통과하는지 확인 (CI 게이트처럼)
 * baseline   → 안정적인 낮은 부하에서 기준 응답 시간 수집
 * load       → TPS 벽(병목)을 찾는 구간별 증가 테스트
 * spike      → 갑작스러운 트래픽 폭증 → HikariCP & Redis 락 한계 측정
 * concurrency → 같은 orderId에 동시 다발 요청 → 멱등성/분산 락 정확성 검증
 */
function buildOptions(profile) {
  const thresholds = {
    // 결제 승인 95% 가 1초 이내여야 정상 (초과 시 DB 커넥션 or 락 병목)
    'step2_payment_confirm_ms': ['p(95)<1000', 'p(99)<2000'],
    // 주문 생성 95% 가 500ms 이내여야 정상
    'step1_order_create_ms': ['p(95)<500'],
    // 전체 HTTP 실패율 5% 이하
    'http_req_failed': ['rate<0.05'],
    // 결제 성공률 90% 이상 (concurrency는 예외 - 중복 차단이 정상)
    'rate_payment_success': profile === 'concurrency' ? ['rate>0'] : ['rate>0.90'],
  };

  switch (profile) {
    case 'smoke':
      return {
        stages: [
          { duration: '30s', target: 5 },
          { duration: '30s', target: 5 },
          { duration: '10s', target: 0 },
        ],
        thresholds,
      };

    case 'baseline':
      return {
        stages: [
          { duration: '30s', target: 10 },
          { duration: '1m',  target: 30 },
          { duration: '3m',  target: 30 },
          { duration: '30s', target: 0 },
        ],
        thresholds,
      };

    case 'load':
      // 구간을 쭉 올리면서 p95가 1초를 넘는 시점(병목)을 찾는다
      return {
        stages: [
          { duration: '30s', target: 20 },
          { duration: '1m',  target: 50 },
          { duration: '1m',  target: 80 },
          { duration: '2m',  target: 100 },
          { duration: '1m',  target: 100 },
          { duration: '30s', target: 0 },
        ],
        thresholds,
      };

    case 'spike':
      // 갑자기 300 VU → HikariCP 커넥션 고갈, Redis 락 경합 최대화
      return {
        stages: [
          { duration: '20s', target: 10 },   // 워밍업
          { duration: '10s', target: 300 },  // 스파이크 (갑자기)
          { duration: '1m',  target: 300 },  // 유지 → 풀 고갈 여부 확인
          { duration: '20s', target: 10 },   // 회복 → 시스템이 정상화되는지
          { duration: '10s', target: 0 },
        ],
        thresholds,
      };

    case 'concurrency':
      // 동일 orderId에 DUPLICATE_VUS 개의 VU가 동시에 결제 확인 요청
      // → 정확히 1개만 성공, 나머지는 차단되어야 함
      return {
        scenarios: {
          concurrency_test: {
            executor: 'shared-iterations',
            vus: DUPLICATE_VUS,
            iterations: DUPLICATE_VUS,
            maxDuration: '2m',
          },
        },
        thresholds: {
          'err_duplicate_ok': [`count>=${Math.floor(DUPLICATE_VUS * 0.8)}`], // 80% 이상 차단 성공
        },
      };

    default:
      return {
        stages: [
          { duration: '30s', target: 10 },
          { duration: '2m',  target: 30 },
          { duration: '30s', target: 0 },
        ],
        thresholds,
      };
  }
}

export const options = {
  ...buildOptions(PROFILE),
  tags: {
    profile: PROFILE,
    base_url: BASE_URL,
  },
};

// ─── 공유 상태 (concurrency 시나리오용) ────────────────────────────────────────

// concurrency 시나리오에서 VU 전체가 같은 orderId를 공격하기 위해
// setup()에서 주문 1개를 만들어 공유한다
let sharedOrderForConcurrency = null;

// ─── Setup ───────────────────────────────────────────────────────────────────

export function setup() {
  // 헬스체크
  const health = http.get(`${BASE_URL}/actuator/health`, {
    tags: { name: 'SETUP_health' },
  });
  if (health.status !== 200) {
    throw new Error(`헬스체크 실패 (status=${health.status}). BASE_URL=${BASE_URL} 확인`);
  }

  console.log(`[SETUP] BASE_URL=${BASE_URL}, PROFILE=${PROFILE}`);
  console.log(`[SETUP] USER_IDS=${USER_IDS.join(',')}, SKU_IDS=${SKU_IDS.join(',')}`);

  // concurrency 시나리오: 미리 주문 1개 생성해서 공유
  if (PROFILE === 'concurrency') {
    const userId = USER_IDS[0];
    const skuId = SKU_IDS[0];

    const orderRes = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({
        orderItems: [{ skuId, quantity: 1 }],
        recipientName: 'k6-concurrency-test',
        recipientPhone: '010-0000-0000',
        shippingAddress: '서울시 동시성 테스트로 1',
        shippingRequest: null,
      }),
      { headers: jsonHeaders(userId), tags: { name: 'SETUP_create_shared_order' } }
    );

    if (orderRes.status !== 200 && orderRes.status !== 201) {
      throw new Error(
        `[concurrency] 공유 주문 생성 실패. status=${orderRes.status}, body=${orderRes.body}`
      );
    }

    const body = orderRes.json();
    sharedOrderForConcurrency = {
      userId,
      orderNumber: body.orderNumber,
      totalAmount: body.totalAmount,
    };

    console.log(`[concurrency] 공유 주문 생성 완료. orderNumber=${sharedOrderForConcurrency.orderNumber}, amount=${sharedOrderForConcurrency.totalAmount}`);
    console.log(`[concurrency] ${DUPLICATE_VUS}개 VU가 동일 orderId로 동시 결제 시도 → 1개만 성공해야 함`);

    return { shared: sharedOrderForConcurrency };
  }

  return { shared: null };
}

// ─── 메인 시나리오 ────────────────────────────────────────────────────────────

export default function (data) {
  if (PROFILE === 'concurrency') {
    runConcurrencyScenario(data.shared);
  } else {
    runNormalScenario();
  }
}

/**
 * 일반 E2E 시나리오:
 *   주문 생성 → 결제 승인
 *
 * 측정 병목:
 *   - `step1_order_create_ms`   : INSERT + 재고 확인 + 장바구니 조회 시간
 *   - `step2_payment_confirm_ms`: Redis 락 + 외부 API(mock) + SELECT FOR UPDATE + INSERT + DELETE
 */
function runNormalScenario() {
  const userId = pickRandom(USER_IDS);
  const skuId = pickRandom(SKU_IDS);

  // ── Step 1: 주문 생성 ──────────────────────────────────────────────────────
  let orderNumber, totalAmount;

  group('Step1_CreateOrder', () => {
    const t = Date.now();
    const res = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({
        orderItems: [{ skuId, quantity: 1 }],
        recipientName: `k6-user-${userId}`,
        recipientPhone: '010-1234-5678',
        shippingAddress: '서울시 테스트구 부하로 42',
        shippingRequest: null,
      }),
      { headers: jsonHeaders(userId), tags: { name: 'POST /api/v1/orders' } }
    );
    orderCreateMs.add(Date.now() - t);

    const ok = check(res, {
      'CreateOrder: 200/201': (r) => r.status === 200 || r.status === 201,
      'CreateOrder: orderNumber 존재': (r) => {
        try { return !!r.json().orderNumber; } catch { return false; }
      },
    });

    if (!ok) {
      errOrderCreate.add(1);
      rateE2eSuccess.add(false);
      return;
    }

    const body = safeJson(res);
    orderNumber = body.orderNumber;
    totalAmount = body.totalAmount;
  });

  if (!orderNumber) return;

  if (THINK_TIME > 0) sleep(THINK_TIME);

  // ── Step 2: 결제 승인 (핵심 병목 지점) ────────────────────────────────────
  //
  // 내부 처리 순서 (PaymentServiceImpl 기준):
  //   [Redis] tryAcquireIdempotency(paymentKey)     →  병목[1]
  //   [Redis] tryAcquireOrderLock(orderId)           →  병목[2]
  //   [HTTP]  tossPaymentsService.confirmPayment()   →  외부 API (mock이면 빠름)
  //   [DB]    findByOrderNumberWithItemsForUpdate     →  병목[3] SELECT FOR UPDATE
  //   [DB]    paymentRepository.save / order.pay()   →  병목[4]
  //   [DB]    cartJPARepository.deleteAllByUserIdAndIdIn → 병목[5]
  //   [Redis] markIdempotencyCompleted + releaseLock
  //
  group('Step2_ConfirmPayment', () => {
    const paymentKey = `k6_${orderNumber}_${__VU}_${Date.now()}`;

    const t = Date.now();
    const res = http.post(
      `${BASE_URL}/api/v1/payments/confirm`,
      JSON.stringify({ paymentKey, orderId: orderNumber, amount: totalAmount }),
      {
        headers: jsonHeaders(userId),
        tags: { name: 'POST /api/v1/payments/confirm' },
        // 409/400도 비즈니스 응답이므로 k6 실패로 집계 안 되게 처리
        responseCallback: http.expectedStatuses(200, 201, 400, 409, 422),
      }
    );
    paymentConfirmMs.add(Date.now() - t);

    // ── 병목 원인별 카운팅 ──────────────────────────────────────
    //
    // 409: Redis 분산 락 획득 실패 "이미 해당 주문의 결제가 처리 중입니다"
    //      → payment:lock:order:{orderId} 경합
    //      → 해결: retry 로직 or 락 TTL 조정
    //
    // 400 with "이미 처리 중인 결제 요청입니다":
    //      → Redis 멱등성 키(IN_PROGRESS) 충돌
    //      → 동일 paymentKey 중복 요청
    //
    // 500:  HikariCP 커넥션 풀 고갈 or DB 락 타임아웃
    //       → "Connection is not available, request timed out"
    //       → 해결: pool-size 증가, 트랜잭션 범위 축소
    if (res.status === 409) {
      errRedisLock.add(1);
    } else if (res.status === 400) {
      const body = safeJson(res);
      if (body && body.message && body.message.includes('처리 중')) {
        errIdempotency.add(1);
      } else {
        errPaymentConfirm.add(1);
      }
    } else if (res.status === 500) {
      const body = safeJson(res);
      if (body && body.message && body.message.toLowerCase().includes('connection')) {
        // HikariCP 타임아웃 → 심각한 커넥션 풀 고갈 신호
        errHikariPool.add(1);
      } else {
        errPaymentConfirm.add(1);
      }
    }

    const ok = check(res, {
      'ConfirmPayment: 200/201': (r) => r.status === 200 || r.status === 201,
      'ConfirmPayment: paymentKey 존재': (r) => {
        try { return !!r.json().paymentKey; } catch { return false; }
      },
      'ConfirmPayment: 상태 DONE': (r) => {
        try { return r.json().status === 'DONE'; } catch { return false; }
      },
      'ConfirmPayment: 응답시간 1초 이내': (r) => r.timings.duration < 1000,
    });

    ratePaymentSuccess.add(ok);
    rateE2eSuccess.add(ok);
    if (!ok) errPaymentConfirm.add(1);
  });
}

/**
 * 중복 결제 동시성 시나리오:
 *   모든 VU가 setup()에서 만든 단 하나의 orderId로 동시에 결제 승인 요청
 *
 * 기대 결과:
 *   - 정확히 1개 VU만 200/201 성공
 *   - 나머지는 400 or 409 (차단되어야 함)
 *
 * 이 테스트가 만약 여러 개 성공하면 → 분산 락 또는 멱등성 버그!
 */
function runConcurrencyScenario(shared) {
  if (!shared) return;

  const { userId, orderNumber, totalAmount } = shared;
  // 각 VU마다 다른 paymentKey → 멱등성 키 충돌은 없고, orderId 락만 경합
  const paymentKey = `k6_concurrent_${orderNumber}_${__VU}`;

  group('ConcurrencyTest_ConfirmPayment', () => {
    const t = Date.now();
    const res = http.post(
      `${BASE_URL}/api/v1/payments/confirm`,
      JSON.stringify({ paymentKey, orderId: orderNumber, amount: totalAmount }),
      {
        headers: jsonHeaders(userId),
        tags: { name: 'CONCURRENCY /api/v1/payments/confirm' },
        responseCallback: http.expectedStatuses(200, 201, 400, 409, 422),
      }
    );
    paymentConfirmMs.add(Date.now() - t);

    const isSuccess = res.status === 200 || res.status === 201;
    const isBlocked = res.status === 400 || res.status === 409;

    // 차단된 경우 → 정상 동작 (멱등성/락이 제대로 동작)
    if (isBlocked) {
      errDuplicateBlocked.add(1);
    }

    check(res, {
      'Concurrency: 성공(1개) or 정상차단(나머지)': () => isSuccess || isBlocked,
      'Concurrency: 중복 INSERT 없음 (5xx 아님)': (r) => r.status < 500,
    });

    ratePaymentSuccess.add(isSuccess);

    if (isSuccess) {
      console.log(`[concurrency] VU ${__VU} 결제 성공 (orderNumber=${orderNumber})`);
    }
  });
}

// ─── Teardown & Summary ──────────────────────────────────────────────────────

export function teardown() {
  console.log(`[TEARDOWN] PROFILE=${PROFILE} 테스트 완료`);
}

export function handleSummary(data) {
  const v = (key, stat) => {
    try {
      const val = data.metrics[key].values[stat];
      if (val === undefined) return 'N/A';
      return typeof val === 'number' ? val.toFixed(2) : String(val);
    } catch {
      return 'N/A';
    }
  };

  const pct = (key, stat) => {
    try {
      return (data.metrics[key].values[stat] * 100).toFixed(1) + '%';
    } catch {
      return 'N/A';
    }
  };

  const cnt = (key) => v(key, 'count');

  const lines = [
    '',
    '╔══════════════════════════════════════════════════════════╗',
    `║   결제 부하테스트 결과   PROFILE=${PROFILE.padEnd(10)}              ║`,
    '╠══════════════════════════════════════════════════════════╣',
    '',
    '▶ [Step1] 주문 생성 응답시간 (step1_order_create_ms)',
    `    p50 : ${v('step1_order_create_ms', 'p(50)')} ms`,
    `    p95 : ${v('step1_order_create_ms', 'p(95)')} ms  ← 500ms 초과시 DB/인덱스 문제`,
    `    p99 : ${v('step1_order_create_ms', 'p(99)')} ms`,
    '',
    '▶ [Step2] 결제 승인 응답시간 (step2_payment_confirm_ms) ← 핵심 병목',
    `    p50 : ${v('step2_payment_confirm_ms', 'p(50)')} ms`,
    `    p95 : ${v('step2_payment_confirm_ms', 'p(95)')} ms  ← 1000ms 초과시 병목 존재`,
    `    p99 : ${v('step2_payment_confirm_ms', 'p(99)')} ms  ← 2000ms 초과시 심각`,
    `    max : ${v('step2_payment_confirm_ms', 'max')} ms`,
    '',
    '▶ [병목 원인 카운터]',
    `    Redis 분산락 실패  (409)          : ${cnt('err_redis_lock_fail')} 건`,
    `      → payment:lock:order:{orderId} 경합이 높으면 DB FOR UPDATE와 겹쳐 병목`,
    `    Redis 멱등성 차단 (400 처리중)    : ${cnt('err_idempotency_blocked')} 건`,
    `      → 동일 paymentKey 중복 요청`,
    `    HikariCP 풀 고갈 의심 (500)       : ${cnt('err_hikari_pool')} 건  ← 0 이어야 함`,
    `      → 0 초과시: maximum-pool-size 증가 또는 트랜잭션 범위 축소 필요`,
    `    주문 생성 에러                    : ${cnt('err_order_create')} 건`,
    `    결제 승인 에러                    : ${cnt('err_payment_confirm')} 건`,
    `    [동시성] 중복 차단 성공           : ${cnt('err_duplicate_ok')} 건`,
    `      → PROFILE=concurrency 에서만 의미있음 (VU수만큼 차단 = 정상)`,
    '',
    '▶ [성공률]',
    `    결제 성공률 : ${pct('rate_payment_success', 'rate')}  ← 90% 이하시 심각`,
    `    E2E 성공률  : ${pct('rate_e2e_success', 'rate')}`,
    '',
    '▶ [전체 HTTP]',
    `    총 요청 수  : ${cnt('http_reqs')} 건`,
    `    실패율      : ${pct('http_req_failed', 'rate')}`,
    `    전체 p95    : ${v('http_req_duration', 'p(95)')} ms`,
    '',
    '▶ [다음 단계]',
    `    1) step2_payment_confirm_ms p95 > 1000ms → DB slow query log 확인`,
    `       → EXPLAIN SELECT * WHERE order_number=? FOR UPDATE 실행`,
    `    2) err_hikari_pool > 0 → HikariCP maximum-pool-size 증가`,
    `       → spring.datasource.hikari.maximum-pool-size=20 (기본 10)`,
    `    3) err_redis_lock_fail 많음 → 분산 락 TTL 또는 retry 로직 검토`,
    `    4) PROFILE=concurrency 에서 성공 건수 > 1 → 분산 락 버그!`,
    '╚══════════════════════════════════════════════════════════╝',
  ].join('\n');

  console.log(lines);
  return { stdout: lines };
}

// ─── 유틸리티 함수 ────────────────────────────────────────────────────────────

function jsonHeaders(userId) {
  return {
    'Content-Type': 'application/json',
    // JWT 토큰을 환경변수로 직접 받거나,
    // 서버가 userId 헤더를 신뢰하는 개발/테스트 모드에서 사용
    // 실제 JWT가 필요하면: Authorization: `Bearer ${토큰}`
    'X-User-Id': String(userId),
    'X-Load-Test': 'true',
  };
}

function parseIds(envStr, fallback) {
  if (!envStr || envStr.trim() === '') return fallback;
  return envStr
    .split(',')
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => !isNaN(n) && n > 0);
}

function pickRandom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function safeJson(res) {
  try { return res.json(); } catch { return null; }
}
