package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.common.lock.DistributedLock;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.entity.CommunityCommentEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityCommentLikeEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostLikeEntity;
import com.homesweet.homesweetback.domain.community.event.PostLikedEvent;
import com.homesweet.homesweetback.domain.community.repository.CommunityCommentLikeRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityCommentRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostLikeRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import com.homesweet.homesweetback.domain.notification.domain.notification.CommunityNotification;
import com.homesweet.homesweetback.domain.notification.service.NotificationSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Community Count 서비스
 *
 * Redis 기반 카운터로 성능 최적화
 * - 조회수: Redis 캐싱 후 주기적으로 DB 동기화
 * - 좋아요: 분산 락으로 동시성 제어
 *
 * @author ohhalim777@gmail.com
 * @date 25. 10. 21.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCountService {

    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;
    private final NotificationSendService notificationSendService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final String VIEW_COUNT_KEY_PREFIX = "community:post:views:";
    private static final String LIKE_COUNT_KEY_PREFIX = "community:post:likes:";
    private static final String COMMENT_LIKE_COUNT_KEY_PREFIX = "community:comment:likes:";
    private static final String DIRTY_VIEW_COUNT_SET = "community:dirty:views";
    private static final long VIEW_COUNT_CACHE_TTL = 24; // 24 hours
    /**
     * 게시글 조회수 증가 (Redis 기반)
     *
     * Redis에서 조회수를 증가시키고, 주기적으로 DB에 동기화
     */
    public void increaseViewCount(Long postId) {
        // 게시글 존재 여부 확인 (캐시 활용)
        if (!postRepository.existsById(postId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        String viewKey = VIEW_COUNT_KEY_PREFIX + postId;

        try {
            // Redis 조회수 증가
            redisTemplate.opsForValue().increment(viewKey);
            redisTemplate.expire(viewKey, VIEW_COUNT_CACHE_TTL, TimeUnit.HOURS);

            // Dirty 마킹 (동기화 대상 표시)
            redisTemplate.opsForSet().add(DIRTY_VIEW_COUNT_SET, postId.toString());

            log.debug("View count increased for post: {}", postId);
        } catch (Exception e) {
            log.error("Failed to increase view count in Redis for post: {}", postId, e);
            // Redis 실패 시 DB 직접 업데이트 (Fallback)
            increaseViewCountInDb(postId);
        }
    }

    /**
     * 조회수 DB 직접 업데이트 (Fallback)
     */
    @Transactional
    protected void increaseViewCountInDb(Long postId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalseWithPessimisticLock(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        post.increaseViewCount();
    }

    /**
     * Redis → DB 조회수 동기화 (스케줄러)
     *
     * 매 5분마다 Redis의 조회수를 DB에 동기화
     */
    @Scheduled(fixedDelay = 300000) // 5분
    @Transactional
    public void syncViewCountToDatabase() {
        Set<String> dirtyPostIds = redisTemplate.opsForSet().members(DIRTY_VIEW_COUNT_SET);

        if (dirtyPostIds == null || dirtyPostIds.isEmpty()) {
            return;
        }

        log.info("Syncing view counts to DB for {} posts", dirtyPostIds.size());

        for (String postIdStr : dirtyPostIds) {
            try {
                Long postId = Long.parseLong(postIdStr);
                String viewKey = VIEW_COUNT_KEY_PREFIX + postId;

                String redisViewCount = redisTemplate.opsForValue().get(viewKey);
                if (redisViewCount == null) {
                    continue;
                }

                int viewCount = Integer.parseInt(redisViewCount);

                // DB 업데이트
                postRepository.findByPostIdAndIsDeletedFalse(postId).ifPresent(post -> {
                    // Redis 값으로 DB 덮어쓰기
                    while (post.getViewCount() < viewCount) {
                        post.increaseViewCount();
                    }
                });

                // Dirty 플래그 제거
                redisTemplate.opsForSet().remove(DIRTY_VIEW_COUNT_SET, postIdStr);

                log.debug("Synced view count for post {}: {}", postId, viewCount);
            } catch (Exception e) {
                log.error("Failed to sync view count for post: {}", postIdStr, e);
            }
        }
    }

    /**
     * 게시글 좋아요 토글화 (분산 락 + Redis 카운터)
     */
    @Transactional
    @DistributedLock(key = "'community:post:like:' + #postId + ':' + #userId", waitTime = 5, leaseTime = 3)
    @CacheEvict(value = "communityPostCache", key = "#postId")
    public void togglePostLike(Long postId, Long userId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        Optional<CommunityPostLikeEntity> existingLike =
                postLikeRepository.findByPostAndUser(post, user);

        String likeCountKey = LIKE_COUNT_KEY_PREFIX + postId;

        if (existingLike.isPresent()) {
            // 좋아요 취소
            postLikeRepository.delete(existingLike.get());
            post.decreaseLikeCount();

            // Redis 카운터 감소
            redisTemplate.opsForValue().decrement(likeCountKey);

            // 좋아요 취소 이벤트 발행
            eventPublisher.publishEvent(new PostLikedEvent(this, postId, userId, false));

            log.debug("Post like removed: post={}, user={}", postId, userId);
        } else {
            // 좋아요 추가
            CommunityPostLikeEntity newLike = CommunityPostLikeEntity.builder()
                    .post(post)
                    .user(user)
                    .build();
            postLikeRepository.save(newLike);
            post.increaseLikeCount();

            // Redis 카운터 증가
            redisTemplate.opsForValue().increment(likeCountKey);
            redisTemplate.expire(likeCountKey, VIEW_COUNT_CACHE_TTL, TimeUnit.HOURS);

            // 알림 전송
            notificationSendService.sendTemplateNotificationToSingleUser(
                    post.getAuthor().getId(),
                    CommunityNotification.NewLike.builder()
                            .userName(user.getName())
                            .postId(post.getPostId())
                            .postTitle(post.getTitle())
                            .build());

            // 좋아요 이벤트 발행
            eventPublisher.publishEvent(new PostLikedEvent(this, postId, userId, true));

            log.debug("Post like added: post={}, user={}", postId, userId);
        }
    }

    /**
     * 게시글 좋아요 확인
     */
    public boolean isPostLiked(Long postId, Long userId) {
        return postLikeRepository.existsByPost_PostIdAndUser_Id(postId, userId);
    }

    /**
     * 댓글 좋아요 토글화 (분산 락 + Redis 카운터)
     */
    @Transactional
    @DistributedLock(key = "'community:comment:like:' + #commentId + ':' + #userId", waitTime = 5, leaseTime = 3)
    @CacheEvict(value = "communityCommentCache", key = "#commentId")
    public void toggleCommentLike(Long commentId, Long userId) {
        CommunityCommentEntity comment = commentRepository.findByCommentIdAndIsDeletedFalse(commentId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        Optional<CommunityCommentLikeEntity> existingLike =
                commentLikeRepository.findByCommentAndUser(comment, user);

        String likeCountKey = COMMENT_LIKE_COUNT_KEY_PREFIX + commentId;

        if (existingLike.isPresent()) {
            // 좋아요 취소
            commentLikeRepository.delete(existingLike.get());
            comment.decreaseLikeCount();

            // Redis 카운터 감소
            redisTemplate.opsForValue().decrement(likeCountKey);

            log.debug("Comment like removed: comment={}, user={}", commentId, userId);
        } else {
            // 좋아요 추가
            CommunityCommentLikeEntity newLike = CommunityCommentLikeEntity.builder()
                    .comment(comment)
                    .user(user)
                    .build();
            commentLikeRepository.save(newLike);
            comment.increaseLikeCount();

            // Redis 카운터 증가
            redisTemplate.opsForValue().increment(likeCountKey);
            redisTemplate.expire(likeCountKey, VIEW_COUNT_CACHE_TTL, TimeUnit.HOURS);

            log.debug("Comment like added: comment={}, user={}", commentId, userId);
        }

        // 알림 전송
        notificationSendService.sendTemplateNotificationToSingleUser(
                comment.getAuthor().getId(),
                CommunityNotification.NewCommentLike.builder()
                        .userName(user.getName())
                        .postId(comment.getPost().getPostId())
                        .postTitle(comment.getPost().getTitle())
                        .commentId(comment.getCommentId())
                        .build());
    }  

    /**
     * 댓글 좋아요 확인
     */
    public boolean isCommentLiked(Long commentId, Long userId) {
        return commentLikeRepository.existsByComment_CommentIdAndUser_Id(commentId, userId);
    }
}