import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

// 커스텀 메트릭
const orderCreationErrors = new Counter('order_creation_errors');
const orderCreationDuration = new Trend('order_creation_duration');

export const options = {
    stages: [
        { duration: '30s', target: 20 },   // 워밍업: 30초 동안 20명까지
        { duration: '1m', target: 100 },   // 램프업: 1분 동안 100명까지
        { duration: '3m', target: 100 },   // 유지: 3분 동안 100명 유지
        { duration: '30s', target: 0 },    // 쿨다운: 30초 동안 0명으로
    ],
    thresholds: {
        // 성능 목표
        'http_req_duration': ['p(95) < 2000'],          // 95% 요청이 2초 이내
        'order_creation_duration': ['p(95) < 1500'],    // 주문 생성 95%가 1.5초 이내
        
        // 성공률 목표
        'checks': ['rate > 0.99'],                       // 전체 체크 성공률 99% 이상
        'order_creation_errors': ['count < 5'],         // 주문 생성 에러 5건 미만
    },
};

// 테스트 데이터
const testUsers = [1, 2, 3, 4, 5];
const testSkus = [1, 2, 3, 4, 5];

export default function () {
    const userId = testUsers[Math.floor(Math.random() * testUsers.length)];
    const skuId = testSkus[Math.floor(Math.random() * testSkus.length)];
    const quantity = Math.floor(Math.random() * 3) + 1;

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userId}`
    };

    // 주문 생성
    const orderPayload = JSON.stringify({
        orderItems: [{
            skuId: skuId,
            quantity: quantity
        }],
        recipientName: `Load Test User ${userId}`,
        recipientPhone: "010-1234-5678",
        shippingAddress: `Seoul Test Address ${userId}`,
        shippingRequest: "문 앞에 놓아주세요",
    });

    const startTime = Date.now();
    const orderRes = http.post(
        'http://localhost:8080/api/v1/orders',
        orderPayload,
        { headers: headers, tags: { name: 'CreateOrder' } }
    );
    orderCreationDuration.add(Date.now() - startTime);

    const success = check(orderRes, {
        '주문 생성 성공 (200/201)': (r) => r.status === 200 || r.status === 201,
        '응답 시간 < 2s': (r) => r.timings.duration < 2000,
        'orderNumber 존재': (r) => {
            try {
                const body = r.json();
                return body.orderNumber !== undefined && body.orderNumber !== null;
            } catch (e) {
                return false;
            }
        },
        'totalAmount 존재': (r) => {
            try {
                const body = r.json();
                return body.totalAmount !== undefined && body.totalAmount > 0;
            } catch (e) {
                return false;
            }
        },
    });

    if (!success) {
        orderCreationErrors.add(1);
        console.error(`주문 생성 실패: userId=${userId}, skuId=${skuId}, status=${orderRes.status}, body=${orderRes.body}`);
    }

    // 사용자가 주문 확인 페이지를 보는 시간
    sleep(2);
}

export function setup() {
    console.log('🚀 주문 생성 부하 테스트 시작');
    console.log(`📊 타겟: localhost:8080`);
    console.log(`👥 테스트 사용자: ${testUsers.length}명`);
    console.log(`📦 테스트 상품: ${testSkus.length}개`);
    console.log('');
    console.log('⚠️  결제 승인은 테스트하지 않습니다 (Mock 구현 필요)');
}

export function teardown(data) {
    console.log('✅ 주문 생성 부하 테스트 완료');
}
