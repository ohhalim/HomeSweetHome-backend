/**
 * Stress Test (스트레스 테스트)
 *
 * 목적: 시스템의 한계를 찾고 과부하 상황에서의 동작을 검증
 *
 * 시나리오:
 * - 2분 동안 점진적으로 사용자를 0명에서 100명까지 증가 (워밍업)
 * - 5분 동안 점진적으로 200명까지 증가 (정상 부하)
 * - 5분 동안 점진적으로 500명까지 증가 (스트레스 상황)
 * - 2분 동안 500명 유지 (임계점 테스트)
 * - 5분 동안 점진적으로 0명까지 감소 (복구 테스트)
 *
 * 총 소요 시간: 19분
 */

import { sleep } from 'k6';
import { randomInt, randomPost, randomComment } from '../modules/config.js';
import { getAuthToken } from '../modules/auth.js';
import * as communityAPI from '../modules/community-api.js';

// 테스트 옵션
export const options = {
  scenarios: {
    community_stress_test: {
      executor: 'ramping-vus',
      stages: [
        { duration: '2m', target: 100 },  // Warm up
        { duration: '5m', target: 200 },  // Normal load
        { duration: '5m', target: 500 },  // Stress load
        { duration: '2m', target: 500 },  // Hold at max
        { duration: '5m', target: 0 },    // Recovery
      ],
      gracefulRampDown: '1m',
    },
  },
  thresholds: {
    // 스트레스 테스트는 임계값을 좀 더 완화
    'http_req_failed': ['rate<0.05'], // 5% 이하 실패율 허용

    // 95% 요청이 2초 이하
    'http_req_duration': ['p(95)<2000'],

    // 99% 요청이 5초 이하
    'http_req_duration{scenario:read}': ['p(99)<5000'],

    // 쓰기 작업은 좀 더 여유롭게
    'http_req_duration{scenario:write}': ['p(99)<10000'],

    // 체크 성공률 > 95% (스트레스 상황에서는 좀 더 완화)
    'checks': ['rate>0.95'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
};

// 시나리오 실행
export default function () {
  const token = getAuthToken(__VU);

  // 스트레스 상황에서는 읽기 비율을 더 높임 (90% 읽기, 10% 쓰기)
  const isReadScenario = Math.random() < 0.9;

  if (isReadScenario) {
    // === 읽기 시나리오 ===
    stressReadScenario(token);
  } else {
    // === 쓰기 시나리오 ===
    stressWriteScenario(token);
  }

  // 스트레스 테스트는 더 짧은 대기 시간
  sleep(randomInt(0, 2));
}

/**
 * 스트레스 읽기 시나리오
 */
function stressReadScenario(token) {
  // 1. 게시글 목록 조회 (페이지 랜덤)
  const page = randomInt(0, 10);
  communityAPI.getPostList(page, 20);
  sleep(0.2);

  // 2. 여러 게시글 상세 조회 (사용자가 여러 글을 빠르게 탐색)
  const viewCount = randomInt(2, 5);
  for (let i = 0; i < viewCount; i++) {
    const postId = randomInt(1, 200);
    communityAPI.getPost(postId);

    // 조회수 증가
    communityAPI.increaseViews(postId);

    sleep(0.1);
  }

  // 3. 댓글 목록 조회
  const postId = randomInt(1, 200);
  communityAPI.getCommentList(postId);
}

/**
 * 스트레스 쓰기 시나리오
 */
function stressWriteScenario(token) {
  // 스트레스 상황에서는 주로 좋아요와 댓글 작성에 집중
  const postId = randomInt(1, 200);

  // 1. 게시글 좋아요 토글
  communityAPI.togglePostLike(token, postId);
  sleep(0.2);

  // 2. 50% 확률로 댓글 작성
  if (Math.random() < 0.5) {
    const commentData = randomComment();
    const commentId = communityAPI.createComment(token, postId, commentData);

    if (commentId) {
      sleep(0.2);
      // 댓글 좋아요
      communityAPI.toggleCommentLike(token, postId, commentId);
    }
  }

  // 3. 10% 확률로 새 게시글 작성
  if (Math.random() < 0.1) {
    const postData = randomPost();
    communityAPI.createPost(token, postData);
  }
}
