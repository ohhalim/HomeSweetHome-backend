import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 커스텀 메트릭
const orderCreationErrors = new Counter('order_creation_errors');
const paymentConfirmErrors = new Counter('payment_confirm_errors');
const orderCreationDuration = new Trend('order_creation_duration');
const paymentConfirmDuration = new Trend('payment_confirm_duration');
const useRealToss = (__ENV.USE_REAL_TOSS || 'false').toLowerCase() === 'true';

export const options = {
    stages: [
        { duration: '30s', target: 10 },   // 워밍업: 30초 동안 10명까지
        { duration: '1m', target: 50 },    // 램프업: 1분 동안 50명까지
        { duration: '2m', target: 50 },    // 유지: 2분 동안 50명 유지
        { duration: '30s', target: 0 },    // 쿨다운: 30초 동안 0명으로
    ],
    thresholds: {
        // 성능 목표
        'http_req_duration': ['p(95) < 3000'],           // 95% 요청이 3초 이내
        'order_creation_duration': ['p(95) < 2000'],     // 주문 생성 95%가 2초 이내
        'payment_confirm_duration': ['p(95) < 2000'],    // 결제 승인 95%가 2초 이내
        
        // 성공률 목표
        'checks': ['rate > 0.95'],                        // 전체 체크 성공률 95% 이상
        'order_creation_errors': ['count < 10'],         // 주문 생성 에러 10건 미만
        'payment_confirm_errors': ['count < 10'],        // 결제 승인 에러 10건 미만
    },
};

// 테스트 데이터 풀
const testUsers = [1, 2, 3, 4, 5]; // 테스트 사용자 ID들
const testSkus = [1, 2, 3, 4, 5];  // 테스트 SKU ID들

export default function () {
    // 랜덤 사용자 및 상품 선택
    const userId = testUsers[Math.floor(Math.random() * testUsers.length)];
    const skuId = testSkus[Math.floor(Math.random() * testSkus.length)];
    const quantity = Math.floor(Math.random() * 3) + 1; // 1~3개 랜덤

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userId}`
    };

    // ===== 1단계: 주문 생성 =====
    const orderPayload = JSON.stringify({
        orderItems: [{
            skuId: skuId,
            quantity: quantity
        }],
        recipientName: `Test User ${userId}`,
        recipientPhone: "010-1234-5678",
        shippingAddress: `Test Address ${userId}`,
        shippingRequest: "배송 전 연락주세요",
    });

    const orderStartTime = Date.now();
    const orderRes = http.post(
        'http://localhost:8080/api/v1/orders',
        orderPayload,
        { headers: headers, tags: { name: 'CreateOrder' } }
    );
    orderCreationDuration.add(Date.now() - orderStartTime);

    const orderSuccess = check(orderRes, {
        '주문 생성 성공 (200/201)': (r) => r.status === 200 || r.status === 201,
        '주문 응답에 orderNumber 포함': (r) => {
            try {
                const body = r.json();
                return body.orderNumber !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    if (!orderSuccess) {
        orderCreationErrors.add(1);
        console.error(`주문 생성 실패: userId=${userId}, skuId=${skuId}, status=${orderRes.status}`);
        return; // 주문 실패 시 결제 단계 스킵
    }

    // 주문 데이터 파싱
    const orderData = orderRes.json();
    const orderNumber = orderData.orderNumber;
    const totalAmount = orderData.totalAmount;

    // 사용자가 결제 정보를 입력하는 시간 시뮬레이션
    sleep(1);

    // ===== 2단계: 결제 승인 =====
    const paymentPayload = JSON.stringify({
        // 이 스크립트는 mock 결제 전용: 실제 Toss 결제키가 아닌 테스트용 키를 사용한다.
        paymentKey: `test_payment_${orderNumber}_${Date.now()}`,
        orderId: orderNumber,
        amount: totalAmount,
    });

    const paymentStartTime = Date.now();
    const paymentRes = http.post(
        'http://localhost:8080/api/v1/payments/confirm',
        paymentPayload,
        { headers: headers, tags: { name: 'ConfirmPayment' } }
    );
    paymentConfirmDuration.add(Date.now() - paymentStartTime);

    const paymentSuccess = check(paymentRes, {
        '결제 승인 성공 (200/201)': (r) => r.status === 200 || r.status === 201,
        '결제 응답 파싱 가능': (r) => {
            try {
                r.json();
                return true;
            } catch (e) {
                return false;
            }
        },
    });

    if (!paymentSuccess) {
        paymentConfirmErrors.add(1);
        console.error(`결제 승인 실패: orderNumber=${orderNumber}, status=${paymentRes.status}, body=${paymentRes.body}`);
    }

    // 결제 완료 후 주문 확인 페이지를 보는 시간
    sleep(2);
}

// 테스트 셋업 (선택적)
export function setup() {
    if (useRealToss) {
        throw new Error('local-payment-test.js는 mock 결제 전용입니다. 실제 Toss 연동 검증은 별도 smoke 스크립트를 사용하세요.');
    }

    console.log('🚀 결제 부하 테스트 시작');
    console.log(`📊 타겟: localhost:8080`);
    console.log(`👥 테스트 사용자: ${testUsers.length}명`);
    console.log(`📦 테스트 상품: ${testSkus.length}개`);
    console.log('🧪 모드: MOCK 결제 (가짜 paymentKey 사용)');
}

// 테스트 종료 후 요약 (선택적)
export function teardown(data) {
    console.log('✅ 결제 부하 테스트 완료');
}
