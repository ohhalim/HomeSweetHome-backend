import http from 'k6/http';
import { check, group } from 'k6';
import { Trend } from 'k6/metrics';
import { config, getHeaders, getPaginationParams } from './config.js';

// Custom metrics
const postCreationTrend = new Trend(config.customMetrics.postCreationTime);
const commentCreationTrend = new Trend(config.customMetrics.commentCreationTime);
const viewIncreaseTrend = new Trend(config.customMetrics.viewIncreaseTime);
const likeToggleTrend = new Trend(config.customMetrics.likeToggleTime);

// ==================== 게시글 API ====================

/**
 * 게시글 생성
 */
export function createPost(token, postData) {
  const url = `${config.baseURL}${config.endpoints.posts}`;
  const payload = JSON.stringify(postData);
  const params = {
    headers: getHeaders(token),
    tags: { name: 'CreatePost', scenario: 'write' },
  };

  const response = http.post(url, payload, params);
  postCreationTrend.add(response.timings.duration);

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
 * 게시글 목록 조회
 */
export function getPostList(page = 0, size = 10) {
  const url = `${config.baseURL}${config.endpoints.posts}${getPaginationParams(page, size)}`;
  const params = {
    tags: { name: 'GetPostList', scenario: 'read' },
  };

  const response = http.get(url, params);

  check(response, {
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

  return response;
}

/**
 * 게시글 단건 조회
 */
export function getPost(postId) {
  const url = `${config.baseURL}${config.endpoints.postDetail(postId)}`;
  const params = {
    tags: { name: 'GetPost', scenario: 'read' },
  };

  const response = http.get(url, params);

  check(response, {
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

  return response;
}

/**
 * 게시글 수정
 */
export function updatePost(token, postId, updateData) {
  const url = `${config.baseURL}${config.endpoints.postDetail(postId)}`;
  const payload = JSON.stringify(updateData);
  const params = {
    headers: getHeaders(token),
    tags: { name: 'UpdatePost', scenario: 'write' },
  };

  const response = http.put(url, payload, params);

  check(response, {
    'update post: status is 200': (r) => r.status === 200,
  });

  return response;
}

/**
 * 게시글 삭제
 */
export function deletePost(token, postId) {
  const url = `${config.baseURL}${config.endpoints.postDetail(postId)}`;
  const params = {
    headers: getHeaders(token),
    tags: { name: 'DeletePost', scenario: 'write' },
  };

  const response = http.del(url, null, params);

  check(response, {
    'delete post: status is 204': (r) => r.status === 204,
  });

  return response;
}

// ==================== 조회수 & 좋아요 API ====================

/**
 * 조회수 증가
 */
export function increaseViews(postId) {
  const url = `${config.baseURL}${config.endpoints.postViews(postId)}`;
  const params = {
    tags: { name: 'IncreaseViews', scenario: 'read' },
  };

  const response = http.post(url, null, params);
  viewIncreaseTrend.add(response.timings.duration);

  check(response, {
    'increase views: status is 200': (r) => r.status === 200,
  });

  return response;
}

/**
 * 게시글 좋아요 토글
 */
export function togglePostLike(token, postId) {
  const url = `${config.baseURL}${config.endpoints.postLikes(postId)}`;
  const params = {
    headers: getHeaders(token),
    tags: { name: 'TogglePostLike', scenario: 'write' },
  };

  const response = http.post(url, null, params);
  likeToggleTrend.add(response.timings.duration);

  check(response, {
    'toggle post like: status is 200': (r) => r.status === 200,
  });

  return response;
}

/**
 * 게시글 좋아요 상태 확인
 */
export function getPostLikeStatus(token, postId) {
  const url = `${config.baseURL}${config.endpoints.postLikesStatus(postId)}`;
  const params = {
    headers: getHeaders(token),
    tags: { name: 'GetPostLikeStatus', scenario: 'read' },
  };

  const response = http.get(url, params);

  check(response, {
    'get post like status: status is 200': (r) => r.status === 200,
  });

  return response;
}

// ==================== 댓글 API ====================

/**
 * 댓글 작성
 */
export function createComment(token, postId, commentData) {
  const url = `${config.baseURL}${config.endpoints.comments(postId)}`;
  const payload = JSON.stringify(commentData);
  const params = {
    headers: getHeaders(token),
    tags: { name: 'CreateComment', scenario: 'write' },
  };

  const response = http.post(url, payload, params);
  commentCreationTrend.add(response.timings.duration);

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
export function getCommentList(postId) {
  const url = `${config.baseURL}${config.endpoints.comments(postId)}`;
  const params = {
    tags: { name: 'GetCommentList', scenario: 'read' },
  };

  const response = http.get(url, params);

  check(response, {
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

  return response;
}

/**
 * 댓글 수정
 */
export function updateComment(token, postId, commentId, updateData) {
  const url = `${config.baseURL}${config.endpoints.commentDetail(postId, commentId)}`;
  const payload = JSON.stringify(updateData);
  const params = {
    headers: getHeaders(token),
    tags: { name: 'UpdateComment', scenario: 'write' },
  };

  const response = http.put(url, payload, params);

  check(response, {
    'update comment: status is 200': (r) => r.status === 200,
  });

  return response;
}

/**
 * 댓글 삭제
 */
export function deleteComment(token, postId, commentId) {
  const url = `${config.baseURL}${config.endpoints.commentDetail(postId, commentId)}`;
  const params = {
    headers: getHeaders(token),
    tags: { name: 'DeleteComment', scenario: 'write' },
  };

  const response = http.del(url, null, params);

  check(response, {
    'delete comment: status is 204': (r) => r.status === 204,
  });

  return response;
}

/**
 * 댓글 좋아요 토글
 */
export function toggleCommentLike(token, postId, commentId) {
  const url = `${config.baseURL}${config.endpoints.commentLikes(postId, commentId)}`;
  const params = {
    headers: getHeaders(token),
    tags: { name: 'ToggleCommentLike', scenario: 'write' },
  };

  const response = http.post(url, null, params);
  likeToggleTrend.add(response.timings.duration);

  check(response, {
    'toggle comment like: status is 200': (r) => r.status === 200,
  });

  return response;
}

/**
 * 댓글 좋아요 상태 확인
 */
export function getCommentLikeStatus(token, postId, commentId) {
  const url = `${config.baseURL}${config.endpoints.commentLikesStatus(postId, commentId)}`;
  const params = {
    headers: getHeaders(token),
    tags: { name: 'GetCommentLikeStatus', scenario: 'read' },
  };

  const response = http.get(url, params);

  check(response, {
    'get comment like status: status is 200': (r) => r.status === 200,
  });

  return response;
}
