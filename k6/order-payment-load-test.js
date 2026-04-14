import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ============================================================
// 설정
//
// [테스트 전략 - 방법 3: 경계 분리 테스트]
// - 주문 생성/조회/취소: k6 부하테스트 (외부 API 없음, 순수 서버 로직)
// - 결제 승인 로직:       PaymentServiceTest (단위 테스트, Mockito)
// - 토스 API 통신:        TossPaymentsServiceIntegrationTest (통합 테스트)
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ORDER_API = `${BASE_URL}/api/v1/orders`;
const PRODUCT_API = `${BASE_URL}/api/v1/products`;
const ORDER_CHECKOUT_WARMUP_VUS = Number(__ENV.ORDER_CHECKOUT_WARMUP_VUS || 20);
const ORDER_CHECKOUT_PEAK_VUS = Number(__ENV.ORDER_CHECKOUT_PEAK_VUS || 60);
const ORDER_READ_WARMUP_VUS = Number(__ENV.ORDER_READ_WARMUP_VUS || 40);
const ORDER_READ_PEAK_VUS = Number(__ENV.ORDER_READ_PEAK_VUS || 120);
const ORDER_HOT_SKU_ID = Number(__ENV.ORDER_HOT_SKU_ID || 0);
const ORDER_HOT_PRODUCT_ID = Number(__ENV.ORDER_HOT_PRODUCT_ID || 0);
const ORDER_AGGRESSIVE_MODE = parseBooleanEnv(__ENV.ORDER_AGGRESSIVE_MODE, false);
const ORDER_SKIP_DETAIL = parseBooleanEnv(__ENV.ORDER_SKIP_DETAIL, ORDER_AGGRESSIVE_MODE);
const ORDER_SKIP_CANCEL = parseBooleanEnv(__ENV.ORDER_SKIP_CANCEL, false);
const ORDER_CREATE_TO_DETAIL_SLEEP_SEC = resolveSecondsEnv(
  'ORDER_CREATE_TO_DETAIL_SLEEP_SEC',
  ORDER_AGGRESSIVE_MODE ? 0 : 0.5
);
const ORDER_DETAIL_TO_CANCEL_SLEEP_SEC = resolveSecondsEnv(
  'ORDER_DETAIL_TO_CANCEL_SLEEP_SEC',
  ORDER_AGGRESSIVE_MODE ? 0 : 0.5
);
const ORDER_CHECKOUT_LOOP_SLEEP_SEC = resolveSecondsEnv(
  'ORDER_CHECKOUT_LOOP_SLEEP_SEC',
  ORDER_AGGRESSIVE_MODE ? 0 : 2
);
const ORDER_FAILURE_SLEEP_SEC = resolveSecondsEnv(
  'ORDER_FAILURE_SLEEP_SEC',
  ORDER_AGGRESSIVE_MODE ? 0 : 2
);
const ORDER_READ_LOOP_SLEEP_SEC = resolveSecondsEnv(
  'ORDER_READ_LOOP_SLEEP_SEC',
  ORDER_AGGRESSIVE_MODE ? 0 : 1
);

// 커스텀 메트릭
const errorRate = new Rate('errors');
const orderCreateLatency = new Trend('order_create_latency', true);
const orderListLatency = new Trend('order_list_latency', true);
const orderDetailLatency = new Trend('order_detail_latency', true);
const orderCancelLatency = new Trend('order_cancel_latency', true);

// ============================================================
// 시나리오 설정
// ============================================================

function parseBooleanEnv(value, defaultValue) {
  if (value === undefined) {
    return defaultValue;
  }
  return ['1', 'true', 'yes', 'on'].includes(String(value).toLowerCase());
}

function resolveSecondsEnv(name, defaultValue) {
  const raw = __ENV[name];
  if (raw === undefined) {
    return defaultValue;
  }
  return Math.max(0, Number(raw));
}

function createRampingVusScenario(warmupVus, peakVus, exec) {
  return {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: warmupVus },
      { duration: '1m', target: peakVus },
      { duration: '2m', target: peakVus },
      { duration: '30s', target: 0 },
    ],
    exec,
  };
}

const scenarios = {};

if (ORDER_CHECKOUT_WARMUP_VUS > 0 || ORDER_CHECKOUT_PEAK_VUS > 0) {
  scenarios.checkout_flow = createRampingVusScenario(
    ORDER_CHECKOUT_WARMUP_VUS,
    ORDER_CHECKOUT_PEAK_VUS,
    'checkoutFlow'
  );
}

if (ORDER_READ_WARMUP_VUS > 0 || ORDER_READ_PEAK_VUS > 0) {
  scenarios.order_read = createRampingVusScenario(
    ORDER_READ_WARMUP_VUS,
    ORDER_READ_PEAK_VUS,
    'orderReadFlow'
  );
}

if (Object.keys(scenarios).length === 0) {
  throw new Error('최소 하나의 주문 시나리오는 활성화되어야 합니다.');
}

export const options = {
  scenarios,
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.05'],
    order_create_latency: ['p(95)<800'],
    order_list_latency: ['p(95)<300'],
    order_detail_latency: ['p(95)<200'],
  },
};

// ============================================================
// Setup: DB에 실제 존재하는 SKU ID 조회
// ============================================================

export function setup() {
  if (ORDER_HOT_SKU_ID > 0) {
    if (ORDER_HOT_PRODUCT_ID > 0) {
      const res = http.get(`${PRODUCT_API}/${ORDER_HOT_PRODUCT_ID}/stocks`);
      if (res.status === 200) {
        try {
          const stocks = JSON.parse(res.body);
          const hotSku = stocks.find((stock) => stock.skuId === ORDER_HOT_SKU_ID);
          if (!hotSku) {
            console.error(
              `ORDER_HOT_PRODUCT_ID=${ORDER_HOT_PRODUCT_ID} 에서 ORDER_HOT_SKU_ID=${ORDER_HOT_SKU_ID} 를 찾지 못했습니다.`
            );
            return { skuIds: [] };
          }
          if (hotSku.stockQuantity <= 0) {
            console.error(`ORDER_HOT_SKU_ID=${ORDER_HOT_SKU_ID} 의 재고가 부족합니다.`);
            return { skuIds: [] };
          }
        } catch (_) {
          console.error('단일 SKU 검증 응답 파싱에 실패했습니다.');
          return { skuIds: [] };
        }
      } else {
        console.error(`단일 SKU 검증 요청 실패: productId=${ORDER_HOT_PRODUCT_ID}, status=${res.status}`);
        return { skuIds: [] };
      }
    }

    console.log(`단일 SKU 집중 타격 모드: skuId=${ORDER_HOT_SKU_ID}`);
    return { skuIds: [ORDER_HOT_SKU_ID] };
  }

  const skuIds = [];
  // 테스트 데이터 상품 ID 목록 (DB에 존재하는 값)
  const productIds = [8801, 8802, 8803, 8804, 8805];

  for (const pid of productIds) {
    const res = http.get(`${PRODUCT_API}/${pid}/stocks`);
    if (res.status === 200) {
      try {
        const stocks = JSON.parse(res.body);
        for (const s of stocks) {
          if (s.skuId && s.stockQuantity > 0) {
            skuIds.push(s.skuId);
          }
        }
      } catch (_) { /* ignore */ }
    }
  }

  console.log(`사용할 SKU ID 목록: ${skuIds.join(', ')}`);
  return { skuIds };
}

// ============================================================
// 헬퍼
// ============================================================

const headers = { 'Content-Type': 'application/json' };

function randomUserId() {
  return Math.floor(Math.random() * 10) + 1;
}

function randomFrom(arr) {
  if (!arr || arr.length === 0) return null;
  return arr[Math.floor(Math.random() * arr.length)];
}

function requestParams(name, customHeaders) {
  const params = { tags: { name } };
  if (customHeaders) {
    params.headers = customHeaders;
  }
  return params;
}

function sleepIfNeeded(seconds) {
  if (seconds > 0) {
    sleep(seconds);
  }
}

// ============================================================
// 주문 생성 → 상세 조회 → 취소 플로우
// (결제 승인은 토스 실환경 없이 불가 → 단위/통합 테스트로 검증)
// ============================================================

export function checkoutFlow(data) {
  const skuIds = data.skuIds;
  if (!skuIds || skuIds.length === 0) {
    console.error('SKU ID 목록이 없습니다. setup()을 확인하세요.');
    sleepIfNeeded(ORDER_FAILURE_SLEEP_SEC);
    return;
  }

  const userId = randomUserId();
  const skuId = randomFrom(skuIds);
  let orderId = null;

  // 1. 주문 생성 (재고 차감 포함)
  group('주문 생성', () => {
    const payload = JSON.stringify({
      orderItems: [{ skuId: skuId, quantity: 1 }],
      recipientName: '테스트수령인',
      recipientPhone: '010-1234-5678',
      shippingAddress: '서울시 강남구 테헤란로 1',
      shippingRequest: '문 앞에 놓아주세요',
    });

    const res = http.post(
      http.url`${ORDER_API}?testUserId=${userId}`,
      payload,
      requestParams('order_create', headers)
    );
    orderCreateLatency.add(res.timings.duration);

    const ok = check(res, { '주문 생성 201': (r) => r.status === 201 });
    errorRate.add(!ok);

    if (res.status === 201) {
      orderId = JSON.parse(res.body).orderId;
    }
  });

  if (!orderId) {
    sleepIfNeeded(ORDER_FAILURE_SLEEP_SEC);
    return;
  }

  sleepIfNeeded(ORDER_CREATE_TO_DETAIL_SLEEP_SEC);

  if (!ORDER_SKIP_DETAIL) {
    group('주문 상세 조회', () => {
      const res = http.get(
        http.url`${ORDER_API}/${orderId}?testUserId=${userId}`,
        requestParams('order_detail')
      );
      orderDetailLatency.add(res.timings.duration);

      const ok = check(res, {
        '주문 상세 200': (r) => r.status === 200,
        '상태 PENDING': (r) => {
          try { return JSON.parse(r.body).status === 'PENDING'; }
          catch { return false; }
        },
      });
      errorRate.add(!ok);
    });
  }

  sleepIfNeeded(ORDER_DETAIL_TO_CANCEL_SLEEP_SEC);

  if (!ORDER_SKIP_CANCEL) {
    group('주문 취소', () => {
      const res = http.del(
        http.url`${ORDER_API}/${orderId}?testUserId=${userId}`,
        null,
        requestParams('order_cancel')
      );
      orderCancelLatency.add(res.timings.duration);

      const ok = check(res, { '주문 취소 204': (r) => r.status === 204 });
      errorRate.add(!ok);
    });
  }

  sleepIfNeeded(ORDER_CHECKOUT_LOOP_SLEEP_SEC);
}

// ============================================================
// 주문 목록 조회 (읽기 전용)
// ============================================================

export function orderReadFlow(data) {
  const userId = randomUserId();

  group('내 주문 목록', () => {
    const res = http.get(
      http.url`${ORDER_API}?testUserId=${userId}&page=0&size=20`,
      requestParams('order_list')
    );
    orderListLatency.add(res.timings.duration);

    const ok = check(res, { '목록 조회 200': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleepIfNeeded(ORDER_READ_LOOP_SLEEP_SEC);
}
