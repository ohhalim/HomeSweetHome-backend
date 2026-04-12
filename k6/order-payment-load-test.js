import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ============================================================
// 설정
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ORDER_API = `${BASE_URL}/api/v1/orders`;
const PAYMENT_API = `${BASE_URL}/api/v1/payments`;

// 커스텀 메트릭
const errorRate = new Rate('errors');
const orderCreateLatency = new Trend('order_create_latency', true);
const orderListLatency = new Trend('order_list_latency', true);
const orderDetailLatency = new Trend('order_detail_latency', true);
const paymentConfirmLatency = new Trend('payment_confirm_latency', true);

// ============================================================
// 시나리오 설정
// ============================================================

export const options = {
  scenarios: {
    // 주문 생성 -> 결제까지 풀 플로우
    checkout_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },   // 웜업
        { duration: '1m', target: 15 },   // 부하 증가
        { duration: '2m', target: 15 },   // 유지
        { duration: '30s', target: 0 },   // 정리
      ],
      exec: 'checkoutFlow',
    },
    // 주문 조회 (읽기)
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
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    errors: ['rate<0.1'],
    order_create_latency: ['p(95)<800'],
    payment_confirm_latency: ['p(95)<1500'],
  },
};

// ============================================================
// 헬퍼
// ============================================================

const headers = { 'Content-Type': 'application/json' };

// 테스트용 유저 ID (DB에 존재하는 값)
function randomUserId() {
  return Math.floor(Math.random() * 10) + 1;
}

// 테스트용 SKU ID (DB에 존재하는 값으로 수정)
const TEST_SKU_IDS = [1, 2, 3, 4, 5];

function randomSkuId() {
  return TEST_SKU_IDS[Math.floor(Math.random() * TEST_SKU_IDS.length)];
}

// ============================================================
// 주문 -> 결제 풀 플로우
// ============================================================

export function checkoutFlow() {
  const userId = randomUserId();
  let orderId = null;
  let orderNumber = null;
  let totalAmount = null;

  // 1. 주문 생성
  group('주문 생성', () => {
    const skuId = randomSkuId();
    const payload = JSON.stringify({
      orderItems: [
        { skuId: skuId, quantity: 1 },
      ],
      recipientName: '테스트수령인',
      recipientPhone: '010-1234-5678',
      shippingAddress: '서울시 강남구 테헤란로 1',
      shippingRequest: '문 앞에 놓아주세요',
    });

    const res = http.post(`${ORDER_API}?testUserId=${userId}`, payload, { headers });
    orderCreateLatency.add(res.timings.duration);

    const ok = check(res, {
      '주문 생성 201': (r) => r.status === 201,
    });
    errorRate.add(!ok);

    if (res.status === 201) {
      const body = JSON.parse(res.body);
      orderId = body.orderId;
      orderNumber = body.orderNumber;
      totalAmount = body.totalAmount;
    }
  });

  if (!orderNumber) {
    sleep(2);
    return;
  }

  sleep(1);

  // 2. 결제 승인 (토스 결제 시뮬레이션)
  group('결제 승인', () => {
    const paymentKey = `test_pk_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const payload = JSON.stringify({
      paymentKey: paymentKey,
      orderId: orderNumber,
      amount: totalAmount,
    });

    const res = http.post(`${PAYMENT_API}/confirm?testUserId=${userId}`, payload, { headers });
    paymentConfirmLatency.add(res.timings.duration);

    const ok = check(res, {
      '결제 승인 200': (r) => r.status === 200,
    });
    errorRate.add(!ok);
  });

  sleep(1);

  // 3. 주문 상세 조회
  group('주문 상세 조회', () => {
    const res = http.get(`${ORDER_API}/${orderId}?testUserId=${userId}`);
    orderDetailLatency.add(res.timings.duration);

    check(res, {
      '주문 상세 200': (r) => r.status === 200,
      '상태 PAID': (r) => {
        try { return JSON.parse(r.body).status === 'PAID'; }
        catch { return false; }
      },
    });
  });

  sleep(2);
}

// ============================================================
// 주문 조회 (읽기 전용)
// ============================================================

export function orderReadFlow() {
  const userId = randomUserId();

  group('내 주문 목록', () => {
    const res = http.get(`${ORDER_API}?testUserId=${userId}`);
    orderListLatency.add(res.timings.duration);

    const ok = check(res, {
      '목록 조회 200': (r) => r.status === 200,
    });
    errorRate.add(!ok);
  });

  sleep(1);
}
