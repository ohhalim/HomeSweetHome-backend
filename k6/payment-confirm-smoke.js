import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 1,
    iterations: 1,
};

function getRequiredEnv(key) {
    const value = __ENV[key];
    if (!value) {
        throw new Error(`${key} 환경변수가 필요합니다.`);
    }
    return value;
}

export default function () {
    const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
    const accessToken = getRequiredEnv('ACCESS_TOKEN');
    const paymentKey = getRequiredEnv('PAYMENT_KEY');
    const orderId = getRequiredEnv('ORDER_ID');
    const amount = Number(getRequiredEnv('AMOUNT'));

    if (Number.isNaN(amount) || amount <= 0) {
        throw new Error('AMOUNT는 0보다 큰 숫자여야 합니다.');
    }

    const payload = JSON.stringify({
        paymentKey,
        orderId,
        amount,
    });

    const headers = {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
    };

    const response = http.post(`${baseUrl}/api/v1/payments/confirm`, payload, { headers });

    check(response, {
        '결제 승인 성공(200/201)': (r) => r.status === 200 || r.status === 201,
    });

    console.log(`status=${response.status}`);
    console.log(`body=${response.body}`);
}
