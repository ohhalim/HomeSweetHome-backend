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

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCountService {

    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final CommunityRedisService redisService;

    @Transactional
    public void increaseViewCount(Long postId) {
        Long result = redisService.incrementPostViewCount(postId);

        // Cache Miss 처리 (Lazy Loading)
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

    @Transactional
    public void togglePostLike(Long postId, Long userId) {
        Long result = redisService.togglePostLike(postId, userId);

        // Cache Miss 처리
        if (result == -1) {
            initPostLikesFromDB(postId);
            result = redisService.togglePostLike(postId, userId);
        }

        boolean isAdded = (result == 1);

        // Event Queue에 적재 (DB 동기화용)
        redisService.addPostLikeEvent(postId, userId, isAdded);

        log.debug("Post like toggled - postId: {}, userId: {}, action: {}",
                postId, userId, isAdded ? "ADDED" : "REMOVED");

        // TODO: 알림 전송 - 트랜잭션 롤백 이슈로 인해 임시 주석 처리
        // User user = userRepository.findById(userId)
        // .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));
        // CommunityPostEntity post =
        // postRepository.findByPostIdAndIsDeletedFalse(postId)
        // .orElseThrow(() -> new
        // CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        // notificationSendService.sendTemplateNotificationToSingleUser(
        // post.getAuthor().getId(),
        // CommunityNotification.NewLike.builder()
        // .userName(user.getName())
        // .postId(post.getPostId())
        // .postTitle(post.getTitle())
        // .build());
    }

    public boolean isPostLiked(Long postId, Long userId) {
        if (!redisService.hasPostLikeKey(postId)) {
            initPostLikesFromDB(postId);
        }
        return redisService.isPostLiked(postId, userId);
    }

    private void initPostLikesFromDB(Long postId) {
        List<Long> userIds = postLikeRepository.findUserIdsByPostId(postId);
        redisService.setPostLikes(postId, userIds);
        log.info("Loaded post likes from DB - postId: {}, count: {}", postId, userIds.size());
    }

    @Transactional
    public void toggleCommentLike(Long commentId, Long userId) {
        Long result = redisService.toggleCommentLike(commentId, userId);

        if (result == -1) {
            initCommentLikesFromDB(commentId);
            result = redisService.toggleCommentLike(commentId, userId);
        }

        boolean isAdded = (result == 1);
        redisService.addCommentLikeEvent(commentId, userId, isAdded);

        log.debug("Comment like toggled - commentId: {}, userId: {}, action: {}",
                commentId, userId, isAdded ? "ADDED" : "REMOVED");

        // TODO: 알림 전송 - 트랜잭션 롤백 이슈로 인해 임시 주석 처리
        // User user = userRepository.findById(userId)
        // .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));
        // CommunityPostEntity post =
        // postRepository.findByPostIdAndIsDeletedFalse(postId)
        // .orElseThrow(() -> new
        // CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        // notificationSendService.sendTemplateNotificationToSingleUser(
        // post.getAuthor().getId(),
        // CommunityNotification.NewLike.builder()
        // .userName(user.getName())
        // .postId(post.getPostId())
        // .postTitle(post.getTitle())
        // .build());
    }

    public boolean isCommentLiked(Long commentId, Long userId) {
        if (!redisService.hasCommentLikeKey(commentId)) {
            initCommentLikesFromDB(commentId);
        }
        return redisService.isCommentLiked(commentId, userId);
    }

    private void initCommentLikesFromDB(Long commentId) {
        List<Long> userIds = commentLikeRepository.findUserIdsByCommentId(commentId);
        redisService.setCommentLikes(commentId, userIds);
        log.info("Loaded comment likes from DB - commentId: {}, count: {}", commentId, userIds.size());
    }

    public Integer getViewCountFromCache(Long postId) {
        Integer viewCount = redisService.getPostViewCount(postId);
        if (viewCount != null)
            return viewCount;

        // DB Fallback
        initViewCountFromDB(postId);
        return redisService.getPostViewCount(postId);
    }

    public Integer getLikeCountFromCache(Long postId) {
        Integer likeCount = redisService.getPostLikeCount(postId);
        if (likeCount != null)
            return likeCount;

        // DB Fallback
        initPostLikesFromDB(postId);
        return redisService.getPostLikeCount(postId);
    }

    public Integer getCommentCountFromCache(Long postId) {
        Integer commentCount = redisService.getPostCommentCount(postId);
        if (commentCount != null)
            return commentCount;

        // DB Fallback
        initCommentCountFromDB(postId);
        return redisService.getPostCommentCount(postId);
    }

    public Integer getCommentLikeCountFromCache(Long commentId) {
        Integer likeCount = redisService.getCommentLikeCount(commentId);
        if (likeCount != null)
            return likeCount;

        // DB Fallback
        initCommentLikesFromDB(commentId);
        return redisService.getCommentLikeCount(commentId);
    }

    /**
     * 여러 댓글의 좋아요수를 한 번에 조회 (Cache miss 시 개별 fallback)
     */
    public Map<Long, Integer> getBulkCommentLikeCountsFromCache(List<Long> commentIds) {
        Map<Long, Integer> result = redisService.getBulkCommentLikeCounts(commentIds);

        // Cache miss 처리
        for (Long commentId : commentIds) {
            if (result.get(commentId) == null) {
                initCommentLikesFromDB(commentId);
                result.put(commentId, redisService.getCommentLikeCount(commentId));
            }
        }
        return result;
    }

    /**
     * 게시글의 모든 카운터(조회수, 좋아요수, 댓글수)를 한 번에 조회
     */
    public PostCounts getPostCounts(Long postId) {
        return PostCounts.ofNullSafe(
                getViewCountFromCache(postId),
                getLikeCountFromCache(postId),
                getCommentCountFromCache(postId));
    }

    /**
     * 여러 게시글의 모든 카운터를 한 번에 조회 (Bulk)
     */
    public Map<Long, PostCounts> getBulkPostCounts(List<Long> postIds) {
        Map<Long, Integer> viewCounts = getBulkViewCountsFromCache(postIds);
        Map<Long, Integer> likeCounts = getBulkLikeCountsFromCache(postIds);
        Map<Long, Integer> commentCounts = getBulkCommentCountsFromCache(postIds);

        Map<Long, PostCounts> result = new java.util.HashMap<>();
        for (Long postId : postIds) {
            result.put(postId, PostCounts.ofNullSafe(
                    viewCounts.get(postId),
                    likeCounts.get(postId),
                    commentCounts.get(postId)));
        }
        return result;
    }

    // ============================================================
    // Bulk Operations (for Post List - MGET)
    // ============================================================

    /**
     * 여러 게시글의 조회수를 한 번에 조회 (Cache miss 시 개별 fallback)
     */
    public Map<Long, Integer> getBulkViewCountsFromCache(List<Long> postIds) {
        Map<Long, Integer> result = redisService.getBulkViewCounts(postIds);

        // Cache miss 처리
        for (Long postId : postIds) {
            if (result.get(postId) == null) {
                initViewCountFromDB(postId);
                result.put(postId, redisService.getPostViewCount(postId));
            }
        }
        return result;
    }

    /**
     * 여러 게시글의 좋아요수를 한 번에 조회 (Cache miss 시 개별 fallback)
     */
    public Map<Long, Integer> getBulkLikeCountsFromCache(List<Long> postIds) {
        Map<Long, Integer> result = redisService.getBulkLikeCounts(postIds);

        // Cache miss 처리
        for (Long postId : postIds) {
            if (result.get(postId) == null) {
                initPostLikesFromDB(postId);
                result.put(postId, redisService.getPostLikeCount(postId));
            }
        }
        return result;
    }

    /**
     * 여러 게시글의 댓글수를 한 번에 조회 (Cache miss 시 개별 fallback)
     */
    public Map<Long, Integer> getBulkCommentCountsFromCache(List<Long> postIds) {
        Map<Long, Integer> result = redisService.getBulkCommentCounts(postIds);

        // Cache miss 처리
        for (Long postId : postIds) {
            if (result.get(postId) == null) {
                initCommentCountFromDB(postId);
                result.put(postId, redisService.getPostCommentCount(postId));
            }
        }
        return result;
    }
}