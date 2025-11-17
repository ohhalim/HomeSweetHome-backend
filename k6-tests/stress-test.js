/**
 * Community Domain Comprehensive Stress Test
 *
 * 커뮤니티 도메인의 모든 API 엔드포인트를 테스트합니다.
 * - 게시글 CRUD (생성, 조회, 수정, 삭제)
 * - 댓글 CRUD (생성, 조회, 수정, 삭제)
 * - 좋아요 기능 (게시글, 댓글)
 * - 조회수 증가
 * - 페이지네이션
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ========== 설정 ==========
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_TOKEN = __ENV.TEST_TOKEN || 'mock-token-for-testing';

// ========== 커스텀 메트릭 ==========
const postCreationRate = new Rate('post_creation_success');
const postCreationTime = new Trend('post_creation_duration');
const commentCreationRate = new Rate('comment_creation_success');
const commentCreationTime = new Trend('comment_creation_duration');
const viewIncreaseRate = new Rate('view_increase_success');
const likeToggleRate = new Rate('like_toggle_success');
const errorCounter = new Counter('errors');

// ========== 테스트 옵션 ==========
export const options = {
  stages: [
    { duration: '2m', target: 100 },   // Warm up to 100 users
    { duration: '5m', target: 200 },   // Normal load
    { duration: '5m', target: 500 },   // Stress load
    { duration: '2m', target: 500 },   // Hold at max
    { duration: '5m', target: 0 },     // Recovery
  ],
  thresholds: {
    'http_req_failed': ['rate<0.05'],
    'http_req_duration': ['p(95)<2000'],
    'http_req_duration{scenario:read}': ['p(99)<5000'],
    'http_req_duration{scenario:write}': ['p(99)<10000'],
    'checks': ['rate>0.95'],
    'post_creation_success': ['rate>0.90'],
    'comment_creation_success': ['rate>0.90'],
    'view_increase_success': ['rate>0.95'],
    'like_toggle_success': ['rate>0.90'],
  },
};

// ========== 유틸리티 함수 ==========
function getHeaders(includeAuth = false) {
  const headers = {
    'Content-Type': 'application/json',
  };
  if (includeAuth) {
    headers['Authorization'] = `Bearer ${TEST_TOKEN}`;
  }
  return headers;
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomItem(array) {
  return array[Math.floor(Math.random() * array.length)];
}

// ========== 테스트 데이터 ==========
const POST_TITLES = [
  '안녕하세요 집들이 후기입니다',
  '북유럽 스타일 인테리어 공유합니다',
  '작은 평수 공간 활용 팁',
  'DIY 가구 제작 경험담',
  '이사 준비 체크리스트',
  '베란다 꾸미기 아이디어',
  '원룸 인테리어 비법',
  '주방 수납 정리 노하우',
  '거실 조명 추천합니다',
  '침실 분위기 연출 방법',
];

const POST_CONTENTS = [
  '오늘 집들이를 했는데 너무 좋았어요. 인테리어에 대해 많은 칭찬을 받았습니다. 준비 과정부터 공유해드릴게요.',
  '북유럽 스타일로 인테리어를 완성했습니다. 심플하면서도 따뜻한 느낌이 나네요. 포인트는 화이트 톤과 우드 소재의 조화입니다.',
  '작은 평수지만 공간을 최대한 활용해봤어요. 수납공간 확보가 중요합니다. 벽면 선반과 수납장을 적극 활용했어요.',
  '직접 가구를 만들어봤는데 생각보다 어렵지 않았어요. 비용도 절약되고 좋습니다. DIY 과정을 단계별로 설명드릴게요.',
  '이사할 때 필요한 체크리스트를 정리해봤습니다. 도움이 되길 바랍니다. 이사 전후로 챙겨야 할 것들이 많더라고요.',
  '베란다를 작은 정원처럼 꾸며봤어요. 식물들이 주는 힐링이 정말 좋습니다. 관리 팁도 함께 공유합니다.',
  '원룸에서도 충분히 멋진 공간을 만들 수 있어요. 제한된 공간이지만 알차게 꾸몄습니다.',
  '주방 수납이 정말 중요하더라고요. 정리 정돈 노하우를 공유합니다. 요리하기 편한 주방이 되었어요.',
  '거실 조명을 바꿨는데 분위기가 완전히 달라졌어요. 추천하는 조명 제품들을 소개합니다.',
  '침실은 편안한 휴식 공간이 되어야 하죠. 색감과 소품으로 아늑한 분위기를 만들었습니다.',
];

const COMMENT_CONTENTS = [
  '정말 멋지네요! 저도 따라해보고 싶어요.',
  '유익한 정보 감사합니다. 많은 도움이 되었어요.',
  '궁금한 점이 있는데 답변 부탁드려요.',
  '대단하세요! 센스가 넘치시네요.',
  '실용적인 팁이네요. 저도 적용해봐야겠어요.',
  '어디서 구매하셨나요? 궁금해요.',
  '비용은 얼마나 드셨어요?',
  '너무 예쁘네요! 부럽습니다.',
  '상세한 설명 감사합니다.',
  '저도 이렇게 해보고 싶네요.',
];

const CATEGORIES = ['INTERIOR', 'FURNITURE', 'LIVING', 'KITCHEN', 'BEDROOM'];

// ========== API 함수들 ==========

/**
 * 게시글 생성
 */
function createPost(token) {
  const url = `${BASE_URL}/api/v1/community/posts`;
  const payload = JSON.stringify({
    title: randomItem(POST_TITLES),
    content: randomItem(POST_CONTENTS),
    category: randomItem(CATEGORIES),
  });

  const startTime = Date.now();
  const response = http.post(url, payload, {
    headers: getHeaders(true),
    tags: { name: 'CreatePost', scenario: 'write' },
  });
  const duration = Date.now() - startTime;

  postCreationTime.add(duration);

  const success = check(response, {
    'create post: status is 201': (r) => r.status === 201,
    'create post: has postId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.postId !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  postCreationRate.add(success);

  if (!success) {
    errorCounter.add(1);
    console.error(`Create post failed: ${response.status} - ${response.body}`);
  }

  if (success && response.status === 201) {
    try {
      return JSON.parse(response.body).postId;
    } catch (e) {
      return null;
    }
  }
  return null;
}

/**
 * 게시글 목록 조회 (페이지네이션)
 */
function getPostList(page = 0, size = 10, sort = 'createdAt', direction = 'DESC') {
  const url = `${BASE_URL}/api/v1/community/posts?page=${page}&size=${size}&sort=${sort}&direction=${direction}`;

  const response = http.get(url, {
    tags: { name: 'GetPostList', scenario: 'read' },
  });

  const success = check(response, {
    'get post list: status is 200': (r) => r.status === 200,
    'get post list: has content': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.content);
      } catch (e) {
        return false;
      }
    },
  });

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 게시글 단건 조회
 */
function getPost(postId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}`;

  const response = http.get(url, {
    tags: { name: 'GetPost', scenario: 'read' },
  });

  const success = check(response, {
    'get post: status is 200': (r) => r.status === 200,
    'get post: has title': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.title !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 게시글 수정
 */
function updatePost(token, postId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}`;
  const payload = JSON.stringify({
    title: randomItem(POST_TITLES) + ' (수정됨)',
    content: randomItem(POST_CONTENTS) + ' [업데이트]',
    category: randomItem(CATEGORIES),
  });

  const response = http.put(url, payload, {
    headers: getHeaders(true),
    tags: { name: 'UpdatePost', scenario: 'write' },
  });

  const success = check(response, {
    'update post: status is 200': (r) => r.status === 200,
  });

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 게시글 삭제
 */
function deletePost(token, postId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}`;

  const response = http.del(url, null, {
    headers: getHeaders(true),
    tags: { name: 'DeletePost', scenario: 'write' },
  });

  const success = check(response, {
    'delete post: status is 204': (r) => r.status === 204,
  });

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 조회수 증가
 */
function increaseViews(postId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}/views`;

  const response = http.post(url, null, {
    tags: { name: 'IncreaseViews', scenario: 'read' },
  });

  const success = check(response, {
    'increase views: status is 200': (r) => r.status === 200,
  });

  viewIncreaseRate.add(success);

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 게시글 좋아요 토글
 */
function togglePostLike(token, postId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}/likes`;

  const response = http.post(url, null, {
    headers: getHeaders(true),
    tags: { name: 'TogglePostLike', scenario: 'write' },
  });

  const success = check(response, {
    'toggle post like: status is 200': (r) => r.status === 200,
  });

  likeToggleRate.add(success);

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 게시글 좋아요 상태 확인
 */
function getPostLikeStatus(token, postId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}/likes/status`;

  const response = http.get(url, {
    headers: getHeaders(true),
    tags: { name: 'GetPostLikeStatus', scenario: 'read' },
  });

  const success = check(response, {
    'get post like status: status is 200': (r) => r.status === 200,
  });

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 댓글 작성
 */
function createComment(token, postId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}/comments`;
  const payload = JSON.stringify({
    content: randomItem(COMMENT_CONTENTS),
  });

  const startTime = Date.now();
  const response = http.post(url, payload, {
    headers: getHeaders(true),
    tags: { name: 'CreateComment', scenario: 'write' },
  });
  const duration = Date.now() - startTime;

  commentCreationTime.add(duration);

  const success = check(response, {
    'create comment: status is 201': (r) => r.status === 201,
    'create comment: has commentId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.commentId !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  commentCreationRate.add(success);

  if (!success) {
    errorCounter.add(1);
  }

  if (success && response.status === 201) {
    try {
      return JSON.parse(response.body).commentId;
    } catch (e) {
      return null;
    }
  }
  return null;
}

/**
 * 댓글 목록 조회
 */
function getCommentList(postId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}/comments`;

  const response = http.get(url, {
    tags: { name: 'GetCommentList', scenario: 'read' },
  });

  const success = check(response, {
    'get comment list: status is 200': (r) => r.status === 200,
    'get comment list: is array': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body);
      } catch (e) {
        return false;
      }
    },
  });

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 댓글 수정
 */
function updateComment(token, postId, commentId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}/comments/${commentId}`;
  const payload = JSON.stringify({
    content: randomItem(COMMENT_CONTENTS) + ' (수정됨)',
  });

  const response = http.put(url, payload, {
    headers: getHeaders(true),
    tags: { name: 'UpdateComment', scenario: 'write' },
  });

  const success = check(response, {
    'update comment: status is 200': (r) => r.status === 200,
  });

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 댓글 삭제
 */
function deleteComment(token, postId, commentId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}/comments/${commentId}`;

  const response = http.del(url, null, {
    headers: getHeaders(true),
    tags: { name: 'DeleteComment', scenario: 'write' },
  });

  const success = check(response, {
    'delete comment: status is 204': (r) => r.status === 204,
  });

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 댓글 좋아요 토글
 */
function toggleCommentLike(token, postId, commentId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}/comments/${commentId}/likes`;

  const response = http.post(url, null, {
    headers: getHeaders(true),
    tags: { name: 'ToggleCommentLike', scenario: 'write' },
  });

  const success = check(response, {
    'toggle comment like: status is 200': (r) => r.status === 200,
  });

  likeToggleRate.add(success);

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

/**
 * 댓글 좋아요 상태 확인
 */
function getCommentLikeStatus(token, postId, commentId) {
  const url = `${BASE_URL}/api/v1/community/posts/${postId}/comments/${commentId}/likes/status`;

  const response = http.get(url, {
    headers: getHeaders(true),
    tags: { name: 'GetCommentLikeStatus', scenario: 'read' },
  });

  const success = check(response, {
    'get comment like status: status is 200': (r) => r.status === 200,
  });

  if (!success) {
    errorCounter.add(1);
  }

  return response;
}

// ========== 메인 테스트 시나리오 ==========
export default function () {
  const token = TEST_TOKEN;
  const userId = __VU;

  // 90% 읽기, 10% 쓰기 (스트레스 테스트)
  const isReadScenario = Math.random() < 0.9;

  if (isReadScenario) {
    // === 읽기 시나리오 ===
    group('Read Operations', function () {
      // 1. 게시글 목록 조회 (여러 페이지)
      const page = randomInt(0, 10);
      getPostList(page, 20);
      sleep(0.2);

      // 2. 여러 게시글 상세 조회
      const viewCount = randomInt(2, 5);
      for (let i = 0; i < viewCount; i++) {
        const postId = randomInt(1, 200);

        getPost(postId);
        sleep(0.1);

        increaseViews(postId);
        sleep(0.1);

        // 50% 확률로 댓글 목록 조회
        if (Math.random() < 0.5) {
          getCommentList(postId);
          sleep(0.1);
        }

        // 30% 확률로 좋아요 상태 확인
        if (Math.random() < 0.3) {
          getPostLikeStatus(token, postId);
          sleep(0.1);
        }
      }
    });

  } else {
    // === 쓰기 시나리오 ===
    group('Write Operations', function () {
      // 시나리오 1: 게시글 작성 및 관련 작업 (40%)
      if (Math.random() < 0.4) {
        // 1. 게시글 작성
        const postId = createPost(token);

        if (postId) {
          sleep(0.5);

          // 2. 작성한 게시글 조회
          getPost(postId);
          sleep(0.3);

          // 3. 댓글 작성
          const commentId = createComment(token, postId);
          sleep(0.3);

          // 4. 게시글 수정
          if (Math.random() < 0.3) {
            updatePost(token, postId);
            sleep(0.3);
          }

          // 5. 게시글 좋아요
          if (Math.random() < 0.5) {
            togglePostLike(token, postId);
          }
        }
      }
      // 시나리오 2: 기존 게시글에 댓글 및 좋아요 (40%)
      else if (Math.random() < 0.75) {
        const postId = randomInt(1, 200);

        // 1. 게시글 조회
        getPost(postId);
        sleep(0.3);

        // 2. 댓글 작성
        const commentId = createComment(token, postId);
        sleep(0.3);

        if (commentId) {
          // 3. 댓글 수정
          if (Math.random() < 0.2) {
            updateComment(token, postId, commentId);
            sleep(0.2);
          }

          // 4. 댓글 좋아요
          if (Math.random() < 0.5) {
            toggleCommentLike(token, postId, commentId);
          }
        }

        // 5. 게시글 좋아요
        if (Math.random() < 0.5) {
          togglePostLike(token, postId);
        }
      }
      // 시나리오 3: CRUD 전체 테스트 (20%)
      else {
        // 1. 게시글 작성
        const postId = createPost(token);

        if (postId) {
          sleep(0.5);

          // 2. 댓글 작성
          const commentId = createComment(token, postId);
          sleep(0.3);

          if (commentId) {
            // 3. 댓글 수정
            updateComment(token, postId, commentId);
            sleep(0.3);

            // 4. 댓글 좋아요
            toggleCommentLike(token, postId, commentId);
            sleep(0.2);

            // 5. 댓글 삭제
            deleteComment(token, postId, commentId);
            sleep(0.2);
          }

          // 6. 게시글 수정
          updatePost(token, postId);
          sleep(0.3);

          // 7. 게시글 좋아요
          togglePostLike(token, postId);
          sleep(0.2);

          // 8. 게시글 삭제
          deletePost(token, postId);
        }
      }
    });
  }

  // 사용자 행동 시뮬레이션: 짧은 대기
  sleep(randomInt(0, 2));
}

// ========== 테스트 완료 후 요약 ==========
export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.json': JSON.stringify(data),
  };
}
