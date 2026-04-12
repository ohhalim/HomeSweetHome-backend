import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ============================================================
// 설정
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API = `${BASE_URL}/api/v1/community`;

// 커스텀 메트릭
const errorRate = new Rate('errors');
const postListLatency = new Trend('post_list_latency', true);
const postDetailLatency = new Trend('post_detail_latency', true);
const postCreateLatency = new Trend('post_create_latency', true);
const viewCountLatency = new Trend('view_count_latency', true);

// ============================================================
// 시나리오 설정
// ============================================================

export const options = {
  scenarios: {
    // 읽기 위주 트래픽 (실제 서비스 비율: 읽기 80%, 쓰기 20%)
    read_heavy: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },  // 웜업
        { duration: '1m', target: 50 },   // 부하 증가
        { duration: '2m', target: 50 },   // 유지
        { duration: '30s', target: 0 },   // 정리
      ],
      exec: 'readScenario',
    },
    // 쓰기 트래픽
    write_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 10 },
        { duration: '2m', target: 10 },
        { duration: '30s', target: 0 },
      ],
      exec: 'writeScenario',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.05'],
    post_list_latency: ['p(95)<300'],
    post_detail_latency: ['p(95)<200'],
  },
};

// ============================================================
// 헬퍼
// ============================================================

const headers = { 'Content-Type': 'application/json' };

function randomUserId() {
  return Math.floor(Math.random() * 10) + 1;
}

/**
 * 목록 API에서 실제 존재하는 postId 목록을 가져온다.
 * 실패하면 빈 배열을 반환한다.
 */
function fetchExistingPostIds() {
  const res = http.get(`${API}/posts?page=0&size=50`);
  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      // Spring Page 응답: { content: [...], ... }
      const content = body.content || [];
      return content.map((p) => p.postId).filter((id) => id != null);
    } catch (_) {
      return [];
    }
  }
  return [];
}

/**
 * 배열에서 랜덤 요소를 가져온다.
 */
function randomFrom(arr) {
  if (!arr || arr.length === 0) return null;
  return arr[Math.floor(Math.random() * arr.length)];
}

// ============================================================
// 읽기 시나리오 (80%)
// ============================================================

export function readScenario() {
  // 매 iteration 시작 시 실제 postId 목록을 가져온다
  let postIds = [];

  group('게시글 목록 조회', () => {
    const page = Math.floor(Math.random() * 3);
    const res = http.get(`${API}/posts?page=${page}&size=10`);
    postListLatency.add(res.timings.duration);
    const ok = check(res, { '목록 조회 200': (r) => r.status === 200 });
    errorRate.add(!ok);

    // 목록에서 postId 수집
    if (res.status === 200) {
      try {
        const body = JSON.parse(res.body);
        const content = body.content || [];
        postIds = content.map((p) => p.postId).filter((id) => id != null);
      } catch (_) { /* ignore */ }
    }
  });

  // postId가 없으면 읽기 테스트 스킵
  if (postIds.length === 0) {
    sleep(1);
    return;
  }

  sleep(0.5);

  const postId = randomFrom(postIds);

  group('게시글 상세 조회', () => {
    const res = http.get(`${API}/posts/${postId}`);
    postDetailLatency.add(res.timings.duration);
    const ok = check(res, { '상세 조회 200': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleep(0.3);

  group('조회수 증가', () => {
    const res = http.post(`${API}/posts/${postId}/views`);
    viewCountLatency.add(res.timings.duration);
    const ok = check(res, { '조회수 증가 200': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleep(0.5);

  group('댓글 목록 조회', () => {
    const res = http.get(`${API}/posts/${postId}/comments`);
    const ok = check(res, { '댓글 조회 200': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleep(1);
}

// ============================================================
// 쓰기 시나리오 (20%)
// ============================================================

export function writeScenario() {
  const userId = randomUserId();
  let createdPostId = null;

  group('게시글 작성', () => {
    const payload = JSON.stringify({
      title: `K6 테스트 게시글 ${Date.now()}`,
      content: '부하테스트 게시글 내용입니다.',
      category: '자유',
    });

    // multipart로 전송 (이미지 없이)
    const res = http.post(
      `${API}/posts?testUserId=${userId}`,
      {
        request: http.file(payload, 'request', 'application/json'),
      }
    );
    postCreateLatency.add(res.timings.duration);
    const ok = check(res, { '게시글 작성 201': (r) => r.status === 201 });
    errorRate.add(!ok);

    if (res.status === 201) {
      try {
        createdPostId = JSON.parse(res.body).postId;
      } catch (_) { /* ignore */ }
    }
  });

  // 작성한 게시글에 댓글 달기
  if (createdPostId) {
    sleep(0.5);

    group('댓글 작성', () => {
      const res = http.post(
        `${API}/posts/${createdPostId}/comments?testUserId=${userId}`,
        JSON.stringify({ content: '테스트 댓글입니다.', parentCommentId: null }),
        { headers }
      );
      const ok = check(res, { '댓글 작성 201': (r) => r.status === 201 });
      errorRate.add(!ok);
    });
  }

  sleep(1);

  // 좋아요 토글 - 실제 존재하는 게시글에만
  if (createdPostId) {
    group('좋아요 토글', () => {
      const res = http.post(`${API}/posts/${createdPostId}/likes?testUserId=${userId}`);
      const ok = check(res, { '좋아요 토글 200': (r) => r.status === 200 });
      errorRate.add(!ok);
    });
  }

  sleep(2);
}
