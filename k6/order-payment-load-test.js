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

// 커스텀 메트릭
const errorRate = new Rate('errors');
const orderCreateLatency = new Trend('order_create_latency', true);
const orderListLatency = new Trend('order_list_latency', true);
const orderDetailLatency = new Trend('order_detail_latency', true);
const orderCancelLatency = new Trend('order_cancel_latency', true);

// ============================================================
// 시나리오 설정
// ============================================================

export const options = {
  scenarios: {
    // 주문 생성 → 조회 플로우
    checkout_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 15 },
        { duration: '2m', target: 15 },
        { duration: '30s', target: 0 },
      ],
      exec: 'checkoutFlow',
    },
    // 주문 목록 조회 (읽기)
    order_read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 30 },
        { duration: '2m', target: 30 },
        { duration: '30s', target: 0 },
      ],
      exec: 'orderReadFlow',
    },
  },
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

// ============================================================
// 주문 생성 → 상세 조회 → 취소 플로우
// (결제 승인은 토스 실환경 없이 불가 → 단위/통합 테스트로 검증)
// ============================================================

export function checkoutFlow(data) {
  const skuIds = data.skuIds;
  if (!skuIds || skuIds.length === 0) {
    console.error('SKU ID 목록이 없습니다. setup()을 확인하세요.');
    sleep(2);
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

    const res = http.post(`${ORDER_API}?testUserId=${userId}`, payload, { headers });
    orderCreateLatency.add(res.timings.duration);

    const ok = check(res, { '주문 생성 201': (r) => r.status === 201 });
    errorRate.add(!ok);

    if (res.status === 201) {
      orderId = JSON.parse(res.body).orderId;
    }
  });

  if (!orderId) {
    sleep(2);
    return;
  }

  sleep(0.5);

  // 2. 주문 상세 조회
  group('주문 상세 조회', () => {
    const res = http.get(`${ORDER_API}/${orderId}?testUserId=${userId}`);
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

  sleep(0.5);

  // 3. 주문 취소 (재고 복원 포함)
  group('주문 취소', () => {
    const res = http.del(`${ORDER_API}/${orderId}?testUserId=${userId}`);
    orderCancelLatency.add(res.timings.duration);

    const ok = check(res, { '주문 취소 204': (r) => r.status === 204 });
    errorRate.add(!ok);
  });

  sleep(2);
}

// ============================================================
// 주문 목록 조회 (읽기 전용)
// ============================================================

export function orderReadFlow(data) {
  const userId = randomUserId();

  group('내 주문 목록', () => {
    const res = http.get(`${ORDER_API}?testUserId=${userId}`);
    orderListLatency.add(res.timings.duration);

    const ok = check(res, { '목록 조회 200': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleep(1);
}
