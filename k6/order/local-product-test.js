import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

export const errors = new Counter("errors");
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
    stages: [
        { duration: "30s", target: 50 },   // 30초 동안 50명까지 증가
        { duration: "1m", target: 100 },   // 1분 동안 100명까지 증가
        { duration: "2m", target: 100 },   // 2분 동안 100명 유지
        { duration: "30s", target: 0 },    // 30초 동안 0명으로 감소
    ],
    thresholds: {
        http_req_duration: ["p(95) < 2000"],  // 95% 요청이 2초 이내
        http_req_failed: ["rate<0.1"],        // 실패율 10% 미만
    },
};

export default function () {
    const url = `${BASE_URL}/api/v1/products/previews?limit=12&sortType=LATEST`;

    const response = http.get(url);

    const ok = check(response, {
        "status is 200": (r) => r.status === 200,
        "response time < 2s": (r) => r.timings.duration < 2000,
    });

    if (!ok) {
        errors.add(1);
    }

    sleep(1);  // 사용자 대기 시간 시뮬레이션
}
