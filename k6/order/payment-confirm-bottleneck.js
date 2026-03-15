/**
 * payment-confirm-bottleneck.js
 *
 * 목적: POST /api/v1/payments/confirm 단일 엔드포인트 집중 부하테스트
 *
 * 측정 병목 지점:
 *   1. Redis 멱등성 키 획득 (SETNX) 응답 시간
 *   2. Redis Order 분산 락 획득 응답 시간
 *   3. DB SELECT FOR UPDATE 대기 시간
 *   4. HikariCP 커넥션 풀 고갈 감지 (connection-timeout 에러)
 *   5. 장바구니 DELETE 쿼리 성능
 *
 * 실행 방법:
 *   k6 run \
 *     -e BASE_URL=http://<APP_IP>:8080 \
 *     -e USER_IDS=1,2,3,4,5 \
 *     -e SKU_IDS=101,102,103 \
 *     -e LOAD_PROFILE=rampup \
 *     k6/order/payment-confirm-bottleneck.js
 *
 * LOAD_PROFILE:
 *   smoke   - 5 VU, 1분  (기본 동작 검증)
 *   rampup  - 10→100 VU, 3분 (기준선 측정)
 *   stress  - 10→200 VU, 6분 (병목 식별)
 *   spike   - 갑자기 300 VU  (커넥션풀 한계)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  parseBool,
  parsePositiveInt,
  parseIdCsv,
  pickRandom,
  randomInt,
  jsonAuthHeaders,
  discoverUserPool,
  discoverSkuPool,
} from './order-test-data.js';

// ─── 커스텀 메트릭 ────────────────────────────────────────────────────────────

// 단계별 응답 시간 (병목 지점 정밀 측정)
const orderCreateDuration = new Trend('bottleneck_order_create_ms', true);
const paymentConfirmDuration = new Trend('bottleneck_payment_confirm_ms', true);

// 에러 카운터: 에러 종류별로 구분해야 병목 원인 파악이 쉽다
const orderErrors = new Counter('bottleneck_order_errors');
const paymentErrors = new Counter('bottleneck_payment_errors');
const hikariTimeoutErrors = new Counter('bottleneck_hikari_timeout');  // 커넥션 풀 고갈
const redisLockFailErrors = new Counter('bottleneck_redis_lock_fail'); // 분산 락 실패
const duplicatePaymentBlocked = new Counter('bottleneck_duplicate_blocked'); // 멱등성 차단

// 성공률
const paymentSuccessRate = new Rate('bottleneck_payment_success_rate');
const e2eSuccessRate = new Rate('bottleneck_e2e_success_rate');

// ─── 환경 변수 ────────────────────────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOAD_PROFILE = String(__ENV.LOAD_PROFILE || 'rampup').toLowerCase();
const RUN_LABEL = __ENV.RUN_LABEL || `bottleneck-payment-${LOAD_PROFILE}-${Date.now()}`;

const USER_IDS = parseIdCsv(__ENV.USER_IDS);
const SKU_IDS = parseIdCsv(__ENV.SKU_IDS);
const USER_SCAN_START = parsePositiveInt(__ENV.USER_SCAN_START, 1);
const USER_SCAN_END = parsePositiveInt(__ENV.USER_SCAN_END, 300);
const MIN_USER_POOL = parsePositiveInt(__ENV.MIN_USER_POOL, 3);
const MIN_SKU_POOL = parsePositiveInt(__ENV.MIN_SKU_POOL, 3);
const DISCOVER_USERS = parseBool(__ENV.DISCOVER_USERS, true);
const DISCOVER_SKUS = parseBool(__ENV.DISCOVER_SKUS, true);

// think time: 0이면 최대 TPS 측정 모드, 1~2이면 현실적 시나리오
const THINK_TIME_SEC = Number(__ENV.THINK_TIME_SEC || '0');

// ─── 부하 프로파일 ────────────────────────────────────────────────────────────

/**
 * 병목 분석 단계:
 *   smoke  : 기본 동작 확인 (에러 없어야 정상)
 *   rampup : 점진적 증가 → p95 응답 시간 기준선 수집
 *   stress : 구간별로 올리며 꺾이는 지점(TPS plateau) 찾기
 *   spike  : 갑자기 최대치 → 커넥션 풀 & Redis 락 폭발 확인
 */
function buildStages(profile) {
  switch (profile) {
    case 'smoke':
      return [
        { duration: '30s', target: 5 },
        { duration: '30s', target: 5 },
        { duration: '10s', target: 0 },
      ];
    case 'rampup':
      return [
        { duration: '30s', target: 10 },
        { duration: '1m',  target: 50 },
        { duration: '2m',  target: 100 },
        { duration: '1m',  target: 100 },
        { duration: '30s', target: 0 },
      ];
    case 'stress':
      return [
        { duration: '30s', target: 20 },
        { duration: '1m',  target: 80 },
        { duration: '2m',  target: 150 },
        { duration: '2m',  target: 200 },
        { duration: '1m',  target: 200 },
        { duration: '30s', target: 0 },
      ];
    case 'spike':
      return [
        { duration: '20s', target: 10 },   // 워밍업
        { duration: '10s', target: 300 },  // 스파이크! → 커넥션 풀 한계 측정
        { duration: '1m',  target: 300 },  // 유지
        { duration: '20s', target: 0 },    // 회복
      ];
    default:
      return [
        { duration: '30s', target: 10 },
        { duration: '2m',  target: 50 },
        { duration: '30s', target: 0 },
      ];
  }
}

export const options = {
  stages: buildStages(LOAD_PROFILE),
  tags: {
    test_type: 'bottleneck',
    load_profile: LOAD_PROFILE,
    run_label: RUN_LABEL,
  },
  thresholds: {
    // 결제 승인 p95 가 1초 초과하면 병목 존재
    'bottleneck_payment_confirm_ms': ['p(95)<1000', 'p(99)<2000'],
    // 주문 생성 p95 가 500ms 초과하면 DB 락/인덱스 문제
    'bottleneck_order_create_ms': ['p(95)<500'],
    // 전체 에러율 5% 이하
    'http_req_failed': ['rate<0.05'],
    // 결제 성공률 90% 이상
    'bottleneck_payment_success_rate': ['rate>0.90'],
  },
};

// ─── Setup: 유저/SKU 풀 사전 수집 ─────────────────────────────────────────────

export function setup() {
  const health = http.get(`${BASE_URL}/actuator/health`, {
    tags: { name: 'SETUP_health' },
  });
  if (health.status !== 200) {
    throw new Error(`헬스체크 실패: ${health.status} - BASE_URL=${BASE_URL}`);
  }

  const users = discoverUserPool({
    baseUrl: BASE_URL,
    authProbePath: '/api/v1/orders',
    candidateUserIds: USER_IDS,
    scanStart: USER_SCAN_START,
    scanEnd: USER_SCAN_END,
    minUsers: MIN_USER_POOL,
    discoverUsers: DISCOVER_USERS,
  });

  if (users.length === 0) {
    throw new Error('유효한 userId를 찾지 못했습니다. USER_IDS 환경변수를 지정하세요.');
  }

  const skuDiscovery = discoverSkuPool({
    baseUrl: BASE_URL,
    candidateSkuIds: SKU_IDS,
    discoverSkus: DISCOVER_SKUS,
    productDiscoveryPages: 3,
    productDiscoveryLimit: 24,
    maxProductsToScan: 60,
    minSkuPool: MIN_SKU_POOL,
    minStock: 1,
  });

  if (skuDiscovery.skuIds.length === 0) {
    throw new Error('유효한 skuId를 찾지 못했습니다.');
  }

  console.log(`[BOTTLENECK TEST] RUN_LABEL=${RUN_LABEL}`);
  console.log(`[BOTTLENECK TEST] BASE_URL=${BASE_URL}, LOAD_PROFILE=${LOAD_PROFILE}`);
  console.log(`[BOTTLENECK TEST] users=${users.length}, skus=${skuDiscovery.skuIds.length}`);

  return { users, skuIds: skuDiscovery.skuIds };
}

// ─── 메인 로직 ───────────────────────────────────────────────────────────────

export default function (data) {
  const userId = pickRandom(data.users);
  const skuId = pickRandom(data.skuIds);
  const quantity = randomInt(1, 2);

  // ── Step 1: 주문 생성 ──────────────────────────────────────────────────────
  const orderStart = Date.now();
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      orderItems: [{ skuId, quantity }],
      recipientName: `k6-bottleneck-${userId}`,
      recipientPhone: '010-0000-0000',
      shippingAddress: '서울시 부하구 테스트로 1',
      shippingRequest: null,
    }),
    {
      headers: jsonAuthHeaders(userId),
      tags: { name: 'Step1_CreateOrder', run_label: RUN_LABEL },
    }
  );
  orderCreateDuration.add(Date.now() - orderStart);

  const orderOk = check(orderRes, {
    'CreateOrder 201': (r) => r.status === 200 || r.status === 201,
    'CreateOrder has orderNumber': (r) => {
      try { return !!r.json().orderNumber; } catch (e) { return false; }
    },
  });

  if (!orderOk) {
    orderErrors.add(1);
    e2eSuccessRate.add(false);
    return;
  }

  const { orderNumber, totalAmount } = orderRes.json();

  if (THINK_TIME_SEC > 0) {
    sleep(THINK_TIME_SEC);
  }

  // ── Step 2: 결제 승인 (핵심 병목 지점) ────────────────────────────────────
  // paymentKey는 실제 Toss mock 서버가 반환하는 값처럼 고유하게 생성
  const paymentKey = `k6_pk_${orderNumber}_${__VU}_${Date.now()}`;

  const paymentStart = Date.now();
  const paymentRes = http.post(
    `${BASE_URL}/api/v1/payments/confirm`,
    JSON.stringify({
      paymentKey,
      orderId: orderNumber,
      amount: totalAmount,
    }),
    {
      headers: jsonAuthHeaders(userId),
      tags: { name: 'Step2_ConfirmPayment', run_label: RUN_LABEL },
      // 409: 중복 처리중, 400: 락 실패 - 이것도 우리가 수집해야 할 정보
      responseCallback: http.expectedStatuses(200, 201, 400, 409),
    }
  );
  paymentConfirmDuration.add(Date.now() - paymentStart);

  // 응답 코드 별로 병목 원인 카운팅
  if (paymentRes.status === 409) {
    // Redis 멱등성 키 충돌 or 분산 락 실패
    redisLockFailErrors.add(1);
  } else if (paymentRes.status === 400) {
    // "이미 처리 중인 결제 요청입니다" → 멱등성 중복 차단
    duplicatePaymentBlocked.add(1);
  } else if (paymentRes.status === 500) {
    // HikariCP 커넥션 타임아웃은 500으로 내려올 가능성
    const body = safeJson(paymentRes);
    if (body && body.message && body.message.includes('Connection is not available')) {
      hikariTimeoutErrors.add(1);
    }
  }

  const paymentOk = check(paymentRes, {
    'ConfirmPayment 200/201': (r) => r.status === 200 || r.status === 201,
    'ConfirmPayment has paymentKey': (r) => {
      try { return !!r.json().paymentKey; } catch (e) { return false; }
    },
    'ConfirmPayment status DONE': (r) => {
      try { return r.json().status === 'DONE'; } catch (e) { return false; }
    },
    'ConfirmPayment responseTime<1000ms': (r) => r.timings.duration < 1000,
  });

  paymentSuccessRate.add(paymentOk);
  e2eSuccessRate.add(paymentOk);

  if (!paymentOk) {
    paymentErrors.add(1);
  }
}

// ─── Teardown & Summary ──────────────────────────────────────────────────────

export function teardown() {
  console.log(`[BOTTLENECK TEST] 완료: ${RUN_LABEL}`);
}

export function handleSummary(data) {
  const m = (key, stat) => {
    const metric = data.metrics[key];
    if (!metric || !metric.values) return 'N/A';
    return metric.values[stat] !== undefined
      ? metric.values[stat].toFixed(2)
      : 'N/A';
  };

  const summary = [
    '',
    '=== 결제 승인 병목 테스트 결과 ===',
    `run_label: ${RUN_LABEL}`,
    `load_profile: ${LOAD_PROFILE}`,
    '',
    '[주문 생성]',
    `  p50: ${m('bottleneck_order_create_ms', 'p(50)')}ms`,
    `  p95: ${m('bottleneck_order_create_ms', 'p(95)')}ms`,
    `  p99: ${m('bottleneck_order_create_ms', 'p(99)')}ms`,
    '',
    '[결제 승인 - 핵심 병목]',
    `  p50: ${m('bottleneck_payment_confirm_ms', 'p(50)')}ms`,
    `  p95: ${m('bottleneck_payment_confirm_ms', 'p(95)')}ms  ← 1000ms 초과 시 병목`,
    `  p99: ${m('bottleneck_payment_confirm_ms', 'p(99)')}ms  ← 2000ms 초과 시 심각`,
    `  max: ${m('bottleneck_payment_confirm_ms', 'max')}ms`,
    '',
    '[병목 원인 카운터]',
    `  Redis 락 실패 (409):        ${m('bottleneck_redis_lock_fail', 'count')}건`,
    `  멱등성 중복 차단:             ${m('bottleneck_duplicate_blocked', 'count')}건`,
    `  HikariCP 타임아웃 의심 (500): ${m('bottleneck_hikari_timeout', 'count')}건`,
    `  주문 생성 에러:               ${m('bottleneck_order_errors', 'count')}건`,
    `  결제 승인 에러:               ${m('bottleneck_payment_errors', 'count')}건`,
    '',
    '[성공률]',
    `  결제 성공률: ${(m('bottleneck_payment_success_rate', 'rate') * 100).toFixed(1)}%  ← 90% 이하 시 심각`,
    `  E2E 성공률:  ${(m('bottleneck_e2e_success_rate', 'rate') * 100).toFixed(1)}%`,
    '',
    '[전체 HTTP 지표]',
    `  총 요청 수:   ${m('http_reqs', 'count')}건`,
    `  실패율:       ${(m('http_req_failed', 'rate') * 100).toFixed(2)}%`,
    `  p95:          ${m('http_req_duration', 'p(95)')}ms`,
    '===================================',
  ].join('\n');

  console.log(summary);

  return {
    stdout: summary,
  };
}

function safeJson(res) {
  try { return res.json(); } catch (e) { return null; }
}
