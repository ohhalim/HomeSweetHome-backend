/**
 * Soak Test (내구성 테스트 / Endurance Test)
 *
 * 목적: 장시간 일정한 부하에서 시스템의 안정성을 검증
 *       메모리 누수, 리소스 고갈, 성능 저하 등을 찾아냄
 *
 * 시나리오:
 * - 5분 동안 점진적으로 사용자를 50명까지 증가
 * - 1시간 동안 50명의 사용자 유지 (실제 운영 부하)
 * - 5분 동안 점진적으로 0명까지 감소
 *
 * 총 소요 시간: 1시간 10분
 *
 * 주의: 이 테스트는 시간이 오래 걸리므로 주의깊게 모니터링하세요.
 *       메모리 사용량, CPU 사용량, 데이터베이스 커넥션 풀 등을 지속적으로 확인하세요.
 */

import { sleep } from 'k6';
import { randomInt, randomPost, randomComment } from '../modules/config.js';
import { getAuthToken } from '../modules/auth.js';
import * as communityAPI from '../modules/community-api.js';

// 테스트 옵션
export const options = {
  scenarios: {
    community_soak_test: {
      executor: 'ramping-vus',
      stages: [
        { duration: '5m', target: 50 },   // Ramp up to 50 users
        { duration: '1h', target: 50 },   // Stay at 50 users for 1 hour
        { duration: '5m', target: 0 },    // Ramp down to 0
      ],
      gracefulRampDown: '1m',
    },
  },
  thresholds: {
    // Soak 테스트는 안정성이 중요하므로 엄격한 임계값 적용
    'http_req_failed': ['rate<0.01'],

    // 성능 저하가 없어야 함
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],

    // 읽기 작업
    'http_req_duration{scenario:read}': ['p(95)<400', 'p(99)<800'],

    // 쓰기 작업
    'http_req_duration{scenario:write}': ['p(95)<1000', 'p(99)<2000'],

    // 체크 성공률 > 99.5%
    'checks': ['rate>0.995'],

    // 커스텀 메트릭도 시간이 지나도 성능 저하가 없어야 함
    'community_post_creation_duration': ['p(95)<1000', 'p(99)<2000'],
    'community_comment_creation_duration': ['p(95)<800', 'p(99)<1500'],
    'community_view_increase_duration': ['p(95)<300', 'p(99)<600'],
    'community_like_toggle_duration': ['p(95)<400', 'p(99)<800'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// 시나리오 실행
export default function () {
  const token = getAuthToken(__VU);

  // 실제 사용 패턴 반영: 75% 읽기, 25% 쓰기
  const isReadScenario = Math.random() < 0.75;

  if (isReadScenario) {
    // === 읽기 시나리오 ===
    soakReadScenario(token);
  } else {
    // === 쓰기 시나리오 ===
    soakWriteScenario(token);
  }

  // 사용자 행동 시뮬레이션: 2~5초 대기 (여유있게)
  sleep(randomInt(2, 5));
}

/**
 * Soak 읽기 시나리오
 */
function soakReadScenario(token) {
  // 1. 게시글 목록 조회
  const page = randomInt(0, 5);
  communityAPI.getPostList(page, 20);
  sleep(randomInt(1, 2));

  // 2. 게시글 상세 조회 (1~3개)
  const viewCount = randomInt(1, 3);
  for (let i = 0; i < viewCount; i++) {
    const postId = randomInt(1, 500); // Soak 테스트 중 누적된 게시글
    communityAPI.getPost(postId);

    // 조회수 증가
    communityAPI.increaseViews(postId);

    sleep(randomInt(1, 2));

    // 50% 확률로 댓글 목록 조회
    if (Math.random() < 0.5) {
      communityAPI.getCommentList(postId);
      sleep(1);
    }
  }

  // 3. 30% 확률로 좋아요 상태 확인
  if (Math.random() < 0.3) {
    const postId = randomInt(1, 500);
    communityAPI.getPostLikeStatus(token, postId);
  }
}

/**
 * Soak 쓰기 시나리오
 */
function soakWriteScenario(token) {
  // 40% 확률로 게시글 작성
  if (Math.random() < 0.4) {
    // 1. 게시글 작성
    const postData = randomPost();
    const postId = communityAPI.createPost(token, postData);

    if (postId) {
      sleep(randomInt(1, 2));

      // 2. 작성한 게시글 확인
      communityAPI.getPost(postId);
      sleep(1);

      // 3. 50% 확률로 자기 게시글에 댓글 작성
      if (Math.random() < 0.5) {
        const commentData = randomComment();
        communityAPI.createComment(token, postId, commentData);
      }
    }
  } else {
    // 60% 확률로 기존 게시글에 상호작용
    const postId = randomInt(1, 500);

    // 1. 게시글 조회
    communityAPI.getPost(postId);
    sleep(1);

    // 2. 70% 확률로 댓글 작성
    if (Math.random() < 0.7) {
      const commentData = randomComment();
      const commentId = communityAPI.createComment(token, postId, commentData);
      sleep(randomInt(1, 2));

      // 댓글 좋아요
      if (commentId && Math.random() < 0.5) {
        communityAPI.toggleCommentLike(token, postId, commentId);
      }
    }

    // 3. 50% 확률로 게시글 좋아요
    if (Math.random() < 0.5) {
      sleep(1);
      communityAPI.togglePostLike(token, postId);
    }
  }
}
