import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomIntBetween, randomItem } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// ==================== Custom Metrics ====================
const errorRate = new Rate('error_rate');
const successRate = new Rate('success_rate');

// Operation Trends
const postViewTrend = new Trend('post_view_duration', true);
const postListTrend = new Trend('post_list_duration', true);
const postCreateTrend = new Trend('post_create_duration', true);
const commentCreateTrend = new Trend('comment_create_duration', true);
const likeToggleTrend = new Trend('like_toggle_duration', true);
const viewCountTrend = new Trend('view_count_duration', true);

// Operation Counters
const reads = new Counter('read_operations');
const writes = new Counter('write_operations');

// ==================== Configuration ====================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_BASE = `${BASE_URL}/api/v1/community`;

const MIN_POST_ID = parseInt(__ENV.MIN_POST_ID) || 1;
const MAX_POST_ID = parseInt(__ENV.MAX_POST_ID) || 205;
const MIN_USER_ID = parseInt(__ENV.MIN_USER_ID) || 1;
const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID) || 10;

// ==================== 실제 트래픽 비율 ====================
// 부하 테스트용: 읽기 80% / 쓰기 20%
// 읽기: 목록 조회 50%, 게시글 조회 50% (조회수 증가 API 포함)
// 쓰기: 좋아요 50%, 댓글 작성 30%, 게시글 작성 20%
const TRAFFIC_WEIGHTS = {
    // 읽기 (80%)
    POST_LIST: 40,      // 50% of 80%
    POST_VIEW: 40,      // 50% of 80% (상세 조회 시 조회수 증가도 함께 호출)

    // 쓰기 (20%)
    LIKE_TOGGLE: 10,    // 50% of 20%
    COMMENT_CREATE: 6,  // 30% of 20%
    POST_CREATE: 4,     // 20% of 20%
};

const TOTAL_WEIGHT = Object.values(TRAFFIC_WEIGHTS).reduce((a, b) => a + b, 0);

// ==================== Test Options ====================
// 🎯 t3.micro EC2 (2 vCPU, 1GB RAM) 테스트용
// - Peak 15 RPS, Spike 30 RPS 목표
// - t3.micro 제한된 리소스에 맞춘 부하
// - 목표: p(95) < 2000ms, Error rate < 10%
export const options = {
    scenarios: {
        // 1️⃣ Warm-up: 시스템 예열 (캐시 워밍, 커넥션 풀 준비)
        warmup: {
            executor: 'constant-arrival-rate',
            rate: 2,
            timeUnit: '1s',
            duration: '20s',
            preAllocatedVUs: 5,
            maxVUs: 10,
            exec: 'realisticTraffic',
            tags: { phase: 'warmup' },
        },

        // 2️⃣ Ramp-up: 점진적 부하 증가 (2 → 15 RPS)
        rampup: {
            executor: 'ramping-arrival-rate',
            startTime: '20s',
            startRate: 2,
            timeUnit: '1s',
            stages: [
                { duration: '20s', target: 5 },    // 일반 트래픽
                { duration: '20s', target: 10 },   // 피크 시간대 진입
                { duration: '20s', target: 15 },   // 피크 트래픽
            ],
            preAllocatedVUs: 10,
            maxVUs: 25,
            exec: 'realisticTraffic',
            tags: { phase: 'rampup' },
        },

        // 3️⃣ Peak: 안정 부하 유지 (15 RPS × 1분)
        peak: {
            executor: 'constant-arrival-rate',
            startTime: '1m20s',
            rate: 15,
            timeUnit: '1s',
            duration: '1m',
            preAllocatedVUs: 15,
            maxVUs: 30,
            exec: 'realisticTraffic',
            tags: { phase: 'peak' },
        },

        // 4️⃣ Spike: 스파이크 시나리오 (최대 30 RPS)
        spike: {
            executor: 'ramping-arrival-rate',
            startTime: '2m20s',
            startRate: 15,
            timeUnit: '1s',
            stages: [
                { duration: '15s', target: 20 },   // 급격한 스파이크
                { duration: '20s', target: 30 },   // 최대 부하
                { duration: '15s', target: 15 },   // 정상 복구
            ],
            preAllocatedVUs: 25,
            maxVUs: 40,
            exec: 'realisticTraffic',
            tags: { phase: 'spike' },
        },

        // 5️⃣ Cooldown: 부하 감소
        cooldown: {
            executor: 'ramping-arrival-rate',
            startTime: '3m10s',
            startRate: 15,
            timeUnit: '1s',
            stages: [
                { duration: '15s', target: 5 },
                { duration: '15s', target: 2 },
            ],
            preAllocatedVUs: 10,
            maxVUs: 20,
            exec: 'realisticTraffic',
            tags: { phase: 'cooldown' },
        },
    },

    thresholds: {
        // 전체 성능 기준 (t3.micro - 관대한 기준)
        http_req_duration: ['p(50)<1000', 'p(95)<2000', 'p(99)<3000'],
        http_req_failed: ['rate<0.10'],      // 에러율 10% 미만 (t3.micro)
        error_rate: ['rate<0.10'],
        success_rate: ['rate>0.90'],

        // 읽기 작업 (t3.micro 기준)
        post_view_duration: ['p(95)<1500'],   // 상세 조회 1.5초 이내
        post_list_duration: ['p(95)<2000'],   // 목록 조회 2초 이내

        // 쓰기 작업 (관대)
        like_toggle_duration: ['p(95)<2000'],    // 좋아요 2초 이내
        comment_create_duration: ['p(95)<2500'], // 댓글 2.5초 이내
        post_create_duration: ['p(95)<3000'],    // 게시글 작성 3초 이내
    },
};

// ==================== Setup ====================
export function setup() {
    console.log('🚀 t3.micro EC2 부하 테스트');
    console.log('═══════════════════════════════════════════════════');
    console.log('🖥️  환경: t3.micro (2 vCPU, 1GB RAM) | EC2');
    console.log(`📍 BASE_URL: ${BASE_URL}`);
    console.log(`📊 Post ID Range: ${MIN_POST_ID} ~ ${MAX_POST_ID}`);
    console.log(`👥 User ID Range: ${MIN_USER_ID} ~ ${MAX_USER_ID}`);
    console.log('');
    console.log('📈 Traffic Distribution:');
    console.log(`   읽기 80%: 목록 조회(${TRAFFIC_WEIGHTS.POST_LIST}%) + 게시글 조회(${TRAFFIC_WEIGHTS.POST_VIEW}%)`);
    console.log(`   쓰기 20%: 좋아요(${TRAFFIC_WEIGHTS.LIKE_TOGGLE}%) + 댓글(${TRAFFIC_WEIGHTS.COMMENT_CREATE}%) + 게시글(${TRAFFIC_WEIGHTS.POST_CREATE}%)`);
    console.log('');
    console.log('⏱️ Test Phases (총 약 4분):');
    console.log('   0:00-0:20  ① Warmup (2 RPS)');
    console.log('   0:20-1:20  ② Ramp-up (2→15 RPS)');
    console.log('   1:20-2:20  ③ Peak (15 RPS 유지)');
    console.log('   2:20-3:10  ④ Spike (15→30 RPS)');
    console.log('   3:10-3:40  ⑤ Cooldown (15→2 RPS)');
    console.log('');
    console.log('🎯 Success Criteria (t3.micro):');
    console.log('   p(95) < 2000ms | Error rate < 10% | Peak 30 RPS');
    console.log('═══════════════════════════════════════════════════');

    // Health check
    const res = http.get(`${API_BASE}/posts?page=0&size=1`);
    if (res.status !== 200) {
        throw new Error(`Health check failed: ${res.status}`);
    }
    console.log('✅ Health check passed\n');

    return { startTime: new Date().toISOString() };
}

export function teardown(data) {
    console.log(`\n🏁 Test completed. Started at: ${data.startTime}`);
}

// ==================== Helper Functions ====================
function getHeaders() {
    return { 'Content-Type': 'application/json' };
}

function getRandomUserId() {
    return randomIntBetween(MIN_USER_ID, MAX_USER_ID);
}

function getRandomPostId() {
    // 80% 확률로 인기 게시글 (상위 20%)
    if (Math.random() < 0.8) {
        const hotRange = Math.max(1, Math.floor((MAX_POST_ID - MIN_POST_ID + 1) * 0.2));
        return randomIntBetween(MAX_POST_ID - hotRange + 1, MAX_POST_ID);
    }
    return randomIntBetween(MIN_POST_ID, MAX_POST_ID);
}

function recordResult(res, trend, isWrite = false) {
    const success = res.status >= 200 && res.status < 400;

    check(res, {
        'status is success': (r) => success,
        'response time < 10s': (r) => r.timings.duration < 10000,
    });

    trend.add(res.timings.duration);
    errorRate.add(!success);
    successRate.add(success);

    if (isWrite) {
        writes.add(1);
    } else {
        reads.add(1);
    }

    return success;
}

// ==================== API Operations ====================

// 📖 게시글 목록 조회
function getPostList() {
    const page = randomIntBetween(0, 10);
    const res = http.get(`${API_BASE}/posts?page=${page}&size=10`, {
        headers: getHeaders(),
        tags: { name: 'GET /posts' },
    });
    recordResult(res, postListTrend);
}

// 📖 게시글 상세 조회 + 조회수 증가
// 조회수를 별도 API로 분리하면 캐싱 전략이 유연해짐
// (게시글 내용은 캐시, 조회수는 비동기/배치 처리 가능)
function getPost() {
    const postId = getRandomPostId();

    // 1. 상세 조회
    const detailRes = http.get(`${API_BASE}/posts/${postId}`, {
        headers: getHeaders(),
        tags: { name: 'GET /posts/:id' },
    });
    recordResult(detailRes, postViewTrend);

    // 2. 조회수 증가 (실제 사용자 행동: 상세 페이지 진입 시 조회수 +1)
    const viewRes = http.post(`${API_BASE}/posts/${postId}/views`, null, {
        headers: getHeaders(),
        tags: { name: 'POST /posts/:id/views' },
    });
    recordResult(viewRes, viewCountTrend);
}

// ✏️ 좋아요 토글
function toggleLike() {
    const postId = getRandomPostId();
    const userId = getRandomUserId();
    const res = http.post(`${API_BASE}/posts/${postId}/likes?testUserId=${userId}`, null, {
        headers: getHeaders(),
        tags: { name: 'POST /posts/:id/likes' },
    });
    recordResult(res, likeToggleTrend, true);
}

// ✏️ 댓글 작성
function createComment() {
    const postId = getRandomPostId();
    const userId = getRandomUserId();
    const payload = JSON.stringify({
        content: `Load test comment ${Date.now()}`,
    });
    const res = http.post(`${API_BASE}/posts/${postId}/comments?testUserId=${userId}`, payload, {
        headers: getHeaders(),
        tags: { name: 'POST /posts/:id/comments' },
    });
    recordResult(res, commentCreateTrend, true);
}

// ✏️ 게시글 작성
function createPost() {
    const userId = getRandomUserId();
    const categories = ['자유게시판', '질문', '정보공유'];

    const formData = {
        request: http.file(
            JSON.stringify({
                title: `Load Test Post ${Date.now()}`,
                content: `Performance test content created at ${new Date().toISOString()}`,
                category: randomItem(categories),
            }),
            'request.json',
            'application/json'
        ),
    };

    const res = http.post(`${API_BASE}/posts?testUserId=${userId}`, formData, {
        tags: { name: 'POST /posts' },
    });
    recordResult(res, postCreateTrend, true);
}

// ==================== Traffic Distribution ====================
function selectOperation() {
    const rand = Math.random() * TOTAL_WEIGHT;
    let cumulative = 0;

    cumulative += TRAFFIC_WEIGHTS.POST_LIST;
    if (rand < cumulative) return 'POST_LIST';

    cumulative += TRAFFIC_WEIGHTS.POST_VIEW;
    if (rand < cumulative) return 'POST_VIEW';

    cumulative += TRAFFIC_WEIGHTS.LIKE_TOGGLE;
    if (rand < cumulative) return 'LIKE_TOGGLE';

    cumulative += TRAFFIC_WEIGHTS.COMMENT_CREATE;
    if (rand < cumulative) return 'COMMENT_CREATE';

    return 'POST_CREATE';
}

// ==================== Main Test Function ====================
export function realisticTraffic() {
    const operation = selectOperation();

    switch (operation) {
        case 'POST_LIST':
            getPostList();
            break;
        case 'POST_VIEW':
            getPost();  // 상세 조회 + 조회수 증가
            break;
        case 'LIKE_TOGGLE':
            toggleLike();
            break;
        case 'COMMENT_CREATE':
            createComment();
            break;
        case 'POST_CREATE':
            createPost();
            break;
    }
}

// ==================== 읽기 전용 테스트 (비교용) ====================
export function readOnlyTraffic() {
    const rand = Math.random();

    if (rand < 0.5) {
        getPostList();      // 목록 조회 50%
    } else {
        getPost();          // 게시글 조회 50% (조회수 증가 포함)
    }
}
