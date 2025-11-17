/**
 * Load Test (부하 테스트)
 *
 * 목적: 일반적인 운영 조건에서 시스템이 예상되는 부하를 처리할 수 있는지 검증
 *
 * 시나리오:
 * - 5분 동안 점진적으로 사용자를 0명에서 100명까지 증가
 * - 10분 동안 100명의 사용자 유지
 * - 5분 동안 점진적으로 0명까지 감소
 *
 * 총 소요 시간: 20분
 */

import { sleep } from 'k6';
import { randomInt, randomPost, randomComment } from '../modules/config.js';
import { getAuthToken } from '../modules/auth.js';
import * as communityAPI from '../modules/community-api.js';

// 테스트 옵션
export const options = {
  scenarios: {
    community_load_test: {
      executor: 'ramping-vus',
      stages: [
        { duration: '5m', target: 100 },  // Ramp up to 100 users over 5 minutes
        { duration: '10m', target: 100 }, // Stay at 100 users for 10 minutes
        { duration: '5m', target: 0 },    // Ramp down to 0 users over 5 minutes
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // HTTP 요청 실패율 < 1%
    'http_req_failed': ['rate<0.01'],

    // 95% 요청이 500ms 이하
    'http_req_duration': ['p(95)<500'],

    // 99% 요청이 1000ms 이하
    'http_req_duration{scenario:read}': ['p(99)<1000'],

    // 쓰기 작업은 좀 더 여유롭게 (99% 요청이 2000ms 이하)
    'http_req_duration{scenario:write}': ['p(99)<2000'],

    // 체크 성공률 > 99%
    'checks': ['rate>0.99'],

    // 커스텀 메트릭 임계값
    'community_post_creation_duration': ['p(95)<1000'],
    'community_comment_creation_duration': ['p(95)<800'],
    'community_view_increase_duration': ['p(95)<300'],
    'community_like_toggle_duration': ['p(95)<400'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// 시나리오 실행
export default function () {
  // 테스트용 토큰 가져오기 (각 VU마다 다른 사용자)
  const token = getAuthToken(__VU);

  // 80% 읽기, 20% 쓰기 비율 (실제 커뮤니티 사용 패턴 반영)
  const isReadScenario = Math.random() < 0.8;

  if (isReadScenario) {
    // === 읽기 시나리오 ===
    readScenario(token);
  } else {
    // === 쓰기 시나리오 ===
    writeScenario(token);
  }

  // 사용자 행동 시뮬레이션: 1~3초 대기
  sleep(randomInt(1, 3));
}

/**
 * 읽기 중심 시나리오
 */
function readScenario(token) {
  // 1. 게시글 목록 조회
  communityAPI.getPostList(0, 20);
  sleep(0.5);

  // 2. 랜덤하게 게시글 상세 조회
  const postId = randomInt(1, 100); // 테스트 데이터가 1~100번 게시글이 있다고 가정
  communityAPI.getPost(postId);

  // 3. 조회수 증가
  communityAPI.increaseViews(postId);
  sleep(0.3);

  // 4. 댓글 목록 조회
  communityAPI.getCommentList(postId);
  sleep(0.5);

  // 5. 30% 확률로 좋아요 상태 확인
  if (Math.random() < 0.3) {
    communityAPI.getPostLikeStatus(token, postId);
  }
}

/**
 * 쓰기 중심 시나리오
 */
function writeScenario(token) {
  // 70% 확률로 게시글 작성 및 관련 작업
  if (Math.random() < 0.7) {
    // 1. 게시글 작성
    const postData = randomPost();
    const postId = communityAPI.createPost(token, postData);

    if (postId) {
      sleep(1);

      // 2. 작성한 게시글 조회
      communityAPI.getPost(postId);
      sleep(0.5);

      // 3. 자기 게시글에 댓글 작성
      const commentData = randomComment();
      communityAPI.createComment(token, postId, commentData);
    }
  } else {
    // 30% 확률로 기존 게시글에 댓글 작성 및 좋아요
    const postId = randomInt(1, 100);

    // 1. 게시글 조회
    communityAPI.getPost(postId);
    sleep(0.5);

    // 2. 댓글 작성
    const commentData = randomComment();
    const commentId = communityAPI.createComment(token, postId, commentData);
    sleep(0.5);

    // 3. 게시글 좋아요
    communityAPI.togglePostLike(token, postId);

    // 4. 댓글 좋아요
    if (commentId) {
      sleep(0.3);
      communityAPI.toggleCommentLike(token, postId, commentId);
    }
  }
}
