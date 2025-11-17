// k6 Performance Test Configuration
export const config = {
  // Base URL - 환경변수로 오버라이드 가능
  baseURL: __ENV.BASE_URL || 'http://localhost:8080',

  // API endpoints
  endpoints: {
    // Auth
    login: '/api/v1/auth/login',

    // Community Posts
    posts: '/api/v1/community/posts',
    postDetail: (postId) => `/api/v1/community/posts/${postId}`,
    postViews: (postId) => `/api/v1/community/posts/${postId}/views`,
    postLikes: (postId) => `/api/v1/community/posts/${postId}/likes`,
    postLikesStatus: (postId) => `/api/v1/community/posts/${postId}/likes/status`,

    // Community Comments
    comments: (postId) => `/api/v1/community/posts/${postId}/comments`,
    commentDetail: (postId, commentId) => `/api/v1/community/posts/${postId}/comments/${commentId}`,
    commentLikes: (postId, commentId) => `/api/v1/community/posts/${postId}/comments/${commentId}/likes`,
    commentLikesStatus: (postId, commentId) => `/api/v1/community/posts/${postId}/comments/${commentId}/likes/status`,
  },

  // HTTP headers
  headers: {
    json: {
      'Content-Type': 'application/json',
    },
    multipart: {
      'Content-Type': 'multipart/form-data',
    },
  },

  // Test data
  testData: {
    users: [
      { email: 'test1@example.com', password: 'Test1234!' },
      { email: 'test2@example.com', password: 'Test1234!' },
      { email: 'test3@example.com', password: 'Test1234!' },
    ],
    posts: {
      titles: [
        '안녕하세요 집들이 후기입니다',
        '북유럽 스타일 인테리어 공유합니다',
        '작은 평수 공간 활용 팁',
        'DIY 가구 제작 경험담',
        '이사 준비 체크리스트',
      ],
      contents: [
        '오늘 집들이를 했는데 너무 좋았어요. 인테리어에 대해 많은 칭찬을 받았습니다.',
        '북유럽 스타일로 인테리어를 완성했습니다. 심플하면서도 따뜻한 느낌이 나네요.',
        '작은 평수지만 공간을 최대한 활용해봤어요. 수납공간 확보가 중요합니다.',
        '직접 가구를 만들어봤는데 생각보다 어렵지 않았어요. 비용도 절약되고 좋습니다.',
        '이사할 때 필요한 체크리스트를 정리해봤습니다. 도움이 되길 바랍니다.',
      ],
    },
    comments: {
      contents: [
        '정말 멋지네요!',
        '저도 따라해보고 싶어요',
        '유익한 정보 감사합니다',
        '궁금한 점이 있는데 답변 부탁드려요',
        '대단하세요!',
      ],
    },
  },

  // Performance thresholds
  thresholds: {
    // HTTP 요청 실패율 < 1%
    'http_req_failed': ['rate<0.01'],

    // 95% 요청이 500ms 이하
    'http_req_duration': ['p(95)<500'],

    // 99% 요청이 1000ms 이하
    'http_req_duration{scenario:read}': ['p(99)<1000'],

    // 쓰기 작업은 좀 더 여유롭게
    'http_req_duration{scenario:write}': ['p(99)<2000'],

    // 체크 성공률 > 99%
    'checks': ['rate>0.99'],
  },

  // Custom metrics 이름
  customMetrics: {
    postCreationTime: 'community_post_creation_duration',
    commentCreationTime: 'community_comment_creation_duration',
    viewIncreaseTime: 'community_view_increase_duration',
    likeToggleTime: 'community_like_toggle_duration',
  },
};

// 공통 헤더 생성 함수
export function getHeaders(token = null) {
  const headers = { ...config.headers.json };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

// 페이지네이션 파라미터 생성
export function getPaginationParams(page = 0, size = 10, sort = 'createdAt', direction = 'DESC') {
  return `?page=${page}&size=${size}&sort=${sort}&direction=${direction}`;
}

// 랜덤 선택 유틸
export function randomItem(array) {
  return array[Math.floor(Math.random() * array.length)];
}

// 랜덤 정수 생성
export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// 임의의 사용자 정보 생성
export function randomUser() {
  return randomItem(config.testData.users);
}

// 임의의 게시글 데이터 생성
export function randomPost() {
  return {
    title: randomItem(config.testData.posts.titles),
    content: randomItem(config.testData.posts.contents),
  };
}

// 임의의 댓글 데이터 생성
export function randomComment() {
  return {
    content: randomItem(config.testData.comments.contents),
  };
}
