package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.community.dto.PostCounts;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.repository.CommunityCommentLikeRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostLikeRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [커뮤니티 카운터 서비스]
 *
 * - 조회수, 댓글수: Redis 캐싱 (빠른 읽기/쓰기)
 * - 좋아요: DB 직접 처리 (정합성 보장)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCountService {

    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final CommunityRedisService redisService;

    // ============================================================
    // [조회수] - Redis 캐싱
    // ============================================================

    @Transactional
    public void increaseViewCount(Long postId) {
        Long result = redisService.incrementPostViewCount(postId);
        if (result == -1) {
            initViewCountFromDB(postId);
            redisService.incrementPostViewCount(postId);
        }
        log.debug("View count increased - postId: {}", postId);
    }

    public void initViewCountFromDB(Long postId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        redisService.setPostViewCount(postId, post.getViewCount());
    }

    // ============================================================
    // [댓글수] - Redis 캐싱
    // ============================================================

    @Transactional
    public void increaseCommentCount(Long postId) {
        Long result = redisService.incrementPostCommentCount(postId);
        if (result == -1) {
            initCommentCountFromDB(postId);
            redisService.incrementPostCommentCount(postId);
        }
        log.debug("Comment count increased - postId: {}", postId);
    }

    @Transactional
    public void decreaseCommentCount(Long postId) {
        Long result = redisService.decreasePostCommentCount(postId);
        if (result == -1) {
            initCommentCountFromDB(postId);
            redisService.decreasePostCommentCount(postId);
        }
        log.debug("Comment count decreased - postId: {}", postId);
    }

    private void initCommentCountFromDB(Long postId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        redisService.setPostCommentCount(postId, post.getCommentCount());
    }

    // ============================================================
    // [게시글 좋아요] - DB 직접 처리
    // ============================================================

    @Transactional
    public void togglePostLike(Long postId, Long userId) {
        boolean exists = postLikeRepository.existsByPost_PostIdAndUser_Id(postId, userId);
        if (exists) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            log.debug("Post like removed - postId: {}, userId: {}", postId, userId);
        } else {
            postLikeRepository.insertPostLike(postId, userId);
            log.debug("Post like added - postId: {}, userId: {}", postId, userId);
        }
    }

    public boolean isPostLiked(Long postId, Long userId) {
        return postLikeRepository.existsByPost_PostIdAndUser_Id(postId, userId);
    }

    // ============================================================
    // [댓글 좋아요] - DB 직접 처리
    // ============================================================

    @Transactional
    public void toggleCommentLike(Long commentId, Long userId) {
        boolean exists = commentLikeRepository.existsByComment_CommentIdAndUser_Id(commentId, userId);
        if (exists) {
            commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId);
            log.debug("Comment like removed - commentId: {}, userId: {}", commentId, userId);
        } else {
            commentLikeRepository.insertCommentLike(commentId, userId);
            log.debug("Comment like added - commentId: {}, userId: {}", commentId, userId);
        }
    }

    public boolean isCommentLiked(Long commentId, Long userId) {
        return commentLikeRepository.existsByComment_CommentIdAndUser_Id(commentId, userId);
    }

    // ============================================================
    // [카운터 조회 - 단건]
    // ============================================================

    public Integer getViewCountFromCache(Long postId) {
        Integer viewCount = redisService.getPostViewCount(postId);
        if (viewCount != null) {
            return viewCount;
        }
        initViewCountFromDB(postId);
        return redisService.getPostViewCount(postId);
    }

    public Integer getLikeCountFromDb(Long postId) {
        return (int) postLikeRepository.countByPost_PostId(postId);
    }

    public Integer getCommentCountFromCache(Long postId) {
        Integer commentCount = redisService.getPostCommentCount(postId);
        if (commentCount != null) {
            return commentCount;
        }
        initCommentCountFromDB(postId);
        return redisService.getPostCommentCount(postId);
    }

    public Integer getCommentLikeCountFromDb(Long commentId) {
        return (int) commentLikeRepository.countByComment_CommentId(commentId);
    }

    // ============================================================
    // [카운터 조회 - 벌크]
    // ============================================================

    public Map<Long, Integer> getBulkCommentLikeCountsFromDb(List<Long> commentIds) {
        Map<Long, Integer> result = new HashMap<>();
        for (Long commentId : commentIds) {
            result.put(commentId, getCommentLikeCountFromDb(commentId));
        }
        return result;
    }

    public PostCounts getPostCounts(Long postId) {
        return PostCounts.ofNullSafe(
                getViewCountFromCache(postId),
                getLikeCountFromDb(postId),
                getCommentCountFromCache(postId));
    }

    public Map<Long, PostCounts> getBulkPostCounts(List<Long> postIds) {
        Map<Long, Integer> viewCounts = getBulkViewCountsFromCache(postIds);
        Map<Long, Integer> commentCounts = getBulkCommentCountsFromCache(postIds);

        Map<Long, PostCounts> result = new HashMap<>();
        for (Long postId : postIds) {
            result.put(postId, PostCounts.ofNullSafe(
                    viewCounts.get(postId),
                    getLikeCountFromDb(postId),
                    commentCounts.get(postId)));
        }
        return result;
    }

    // ============================================================
    // [벌크 조회 - 내부 메서드]
    // ============================================================

    public Map<Long, Integer> getBulkViewCountsFromCache(List<Long> postIds) {
        Map<Long, Integer> result = redisService.getBulkViewCounts(postIds);
        for (Long postId : postIds) {
            if (result.get(postId) == null) {
                initViewCountFromDB(postId);
                result.put(postId, redisService.getPostViewCount(postId));
            }
        }
        return result;
    }

    public Map<Long, Integer> getBulkCommentCountsFromCache(List<Long> postIds) {
        Map<Long, Integer> result = redisService.getBulkCommentCounts(postIds);
        for (Long postId : postIds) {
            if (result.get(postId) == null) {
                initCommentCountFromDB(postId);
                result.put(postId, redisService.getPostCommentCount(postId));
            }
        }
        return result;
    }
}