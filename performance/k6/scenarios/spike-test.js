/**
 * Spike Test (스파이크 테스트)
 *
 * 목적: 갑작스러운 트래픽 급증 상황에서 시스템의 동작을 검증
 *        (예: 인기 게시글이 올라왔을 때, 이벤트 시작 시)
 *
 * 시나리오:
 * - 1분 동안 10명으로 워밍업
 * - 30초 만에 갑자기 500명으로 급증 (스파이크!)
 * - 3분 동안 500명 유지
 * - 30초 만에 10명으로 급감
 * - 1분 동안 10명 유지 (복구 확인)
 * - 다시 30초 만에 1000명으로 급증 (더 큰 스파이크!)
 * - 2분 동안 1000명 유지
 * - 1분 동안 0명으로 감소
 *
 * 총 소요 시간: 10분
 */

import { sleep } from 'k6';
import { randomInt, randomPost, randomComment } from '../modules/config.js';
import { getAuthToken } from '../modules/auth.js';
import * as communityAPI from '../modules/community-api.js';

// 테스트 옵션
export const options = {
  scenarios: {
    community_spike_test: {
      executor: 'ramping-vus',
      stages: [
        { duration: '1m', target: 10 },    // Warm up to 10 users
        { duration: '30s', target: 500 },  // SPIKE! Jump to 500 users
        { duration: '3m', target: 500 },   // Stay at spike level
        { duration: '30s', target: 10 },   // Drop back down
        { duration: '1m', target: 10 },    // Recovery period
        { duration: '30s', target: 1000 }, // BIGGER SPIKE! Jump to 1000 users
        { duration: '2m', target: 1000 },  // Stay at higher spike
        { duration: '1m', target: 0 },     // Ramp down to 0
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // 스파이크 테스트는 실패를 더 허용
    'http_req_failed': ['rate<0.10'], // 10% 이하 실패율 허용

    // 응답 시간도 더 여유롭게
    'http_req_duration': ['p(95)<5000'],
    'http_req_duration{scenario:read}': ['p(99)<10000'],

    // 체크 성공률 > 90%
    'checks': ['rate>0.90'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
};

// 시나리오 실행
export default function () {
  const token = getAuthToken(__VU);

  // 스파이크 상황에서는 주로 읽기 작업 (95% 읽기, 5% 쓰기)
  // 예: 핫한 게시글이 올라와서 모두가 조회
  const isReadScenario = Math.random() < 0.95;

  if (isReadScenario) {
    // === 읽기 시나리오 ===
    spikeReadScenario(token);
  } else {
    // === 쓰기 시나리오 ===
    spikeWriteScenario(token);
  }

  // 스파이크 상황에서는 매우 짧은 대기 시간
  sleep(randomInt(0, 1));
}

/**
 * 스파이크 읽기 시나리오 (핫한 게시글 집중 조회)
 */
function spikeReadScenario(token) {
  // 시뮬레이션: 특정 게시글(1~10번)이 핫하다고 가정
  const hotPostIds = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  // 80% 확률로 핫한 게시글 조회
  let postId;
  if (Math.random() < 0.8) {
    postId = hotPostIds[randomInt(0, hotPostIds.length - 1)];
  } else {
    postId = randomInt(11, 200);
  }

  // 1. 게시글 상세 조회
  communityAPI.getPost(postId);

  // 2. 조회수 증가 (동시성 제어 테스트)
  communityAPI.increaseViews(postId);

  // 3. 50% 확률로 댓글 목록 조회
  if (Math.random() < 0.5) {
    sleep(0.1);
    communityAPI.getCommentList(postId);
  }

  // 4. 30% 확률로 좋아요 상태 확인
  if (Math.random() < 0.3) {
    sleep(0.1);
    communityAPI.getPostLikeStatus(token, postId);
  }
}

/**
 * 스파이크 쓰기 시나리오
 */
function spikeWriteScenario(token) {
  const hotPostIds = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
  const postId = hotPostIds[randomInt(0, hotPostIds.length - 1)];

  // 1. 70% 확률로 댓글 작성 (핫한 게시글에 댓글 몰림)
  if (Math.random() < 0.7) {
    const commentData = randomComment();
    communityAPI.createComment(token, postId, commentData);
  }

  // 2. 50% 확률로 좋아요 토글
  if (Math.random() < 0.5) {
    sleep(0.1);
    communityAPI.togglePostLike(token, postId);
  }
}
