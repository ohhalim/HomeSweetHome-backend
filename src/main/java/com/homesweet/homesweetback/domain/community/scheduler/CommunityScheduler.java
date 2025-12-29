package com.homesweet.homesweetback.domain.community.scheduler;

import com.homesweet.homesweetback.domain.community.repository.CommunityCommentLikeRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityCommentRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostLikeRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import com.homesweet.homesweetback.domain.community.service.CommunityCountService;
import com.homesweet.homesweetback.domain.community.service.CommunityRedisService;
import com.homesweet.homesweetback.domain.community.service.CommunityRedisService.LikeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 커뮤니티 데이터 동기화 스케줄러
 * Redis → DB 배치 동기화
 *
 * 스케줄러 설정은 application.yml의 community.scheduler 섹션에서 관리
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommunityScheduler {

    private final CommunityRedisService redisService;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final CommunityCommentRepository commentRepository;
    private final TransactionTemplate transactionTemplate;
    private final CommunityCountService communityCountService;

    /**
     * 인기 게시글 캐시 워밍업 - 서버 시작 시 및 주기적 실행
     */
    @Scheduled(initialDelay = 5000, fixedDelayString = "${community.scheduler.warmup-delay:3600000}")
    public void warmupPopularPostsCache() {
        log.info("Starting cache warmup for popular posts...");

        try {
            var recentPosts = communityPostRepository.findByIsDeletedFalse(
                    PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "postId")));

            int warmupCount = 0;
            for (var post : recentPosts.getContent()) {
                Long postId = post.getPostId();

                if (!redisService.hasPostLikeKey(postId)) {
                    List<Long> userIds = postLikeRepository.findUserIdsByPostId(postId);
                    redisService.setPostLikes(postId, userIds);
                    warmupCount++;
                }

                // 카운터 워밍업
                communityCountService.getPostCounts(postId);
            }

            log.info("Cache warmup completed - posts: {}, likes warmed: {}",
                    recentPosts.getContent().size(), warmupCount);

        } catch (Exception e) {
            log.error("Failed to warmup cache", e);
        }
    }

    /**
     * 조회수 동기화 (Redis → DB)
     */
    @Scheduled(initialDelayString = "${community.scheduler.view-sync-delay:100000}", fixedDelayString = "${community.scheduler.view-sync-delay:110000}")
    public void syncViewCounts() {
        Map<Long, Integer> viewCounts = redisService.scanAndCollectViewCounts();

        if (viewCounts.isEmpty()) {
            return;
        }

        syncCountsToDb(viewCounts, "view",
                (postId, count) -> communityPostRepository.updateViewCount(postId, count),
                redisService::deletePostViewKey);

        log.info("View count sync completed - processed: {}", viewCounts.size());
    }

    /**
     * 댓글수 동기화 (Redis → DB)
     */
    @Scheduled(initialDelayString = "${community.scheduler.comment-sync-delay:200000}", fixedDelayString = "${community.scheduler.comment-sync-delay:210000}")
    public void syncCommentCounts() {
        Map<Long, Integer> commentCounts = redisService.scanAndCollectCommentCounts();

        if (commentCounts.isEmpty()) {
            return;
        }

        syncCountsToDb(commentCounts, "comment",
                (postId, count) -> communityPostRepository.setCommentCount(postId, count),
                redisService::deletePostCommentCountKey);

        log.info("Comment count sync completed - processed: {}", commentCounts.size());
    }

    /**
     * 게시글 좋아요 이벤트 동기화 (Redis Queue → DB)
     */
    @Scheduled(fixedDelayString = "${community.scheduler.like-sync-delay:300000}")
    public void syncPostLikeEvents() {
        List<LikeEvent> events = redisService.pollPostLikeEvents(1000);

        if (events.isEmpty()) {
            return;
        }

        int[] counts = syncLikeEvents(events,
                postLikeRepository::insertPostLike,
                postLikeRepository::deleteByPostIdAndUserId);

        redisService.trimPostLikeEvents(events.size());

        log.info("Post like events synced - total: {}, added: {}, removed: {}",
                events.size(), counts[0], counts[1]);
    }

    /**
     * 게시글 좋아요 개수 동기화 (Redis → DB)
     */
    @Scheduled(fixedDelayString = "${community.scheduler.like-sync-delay:300000}", initialDelayString = "${community.scheduler.like-sync-delay:310000}")
    public void syncPostLikeCounts() {
        Map<Long, Integer> likeCounts = redisService.scanAndCollectPostLikeCounts();

        if (likeCounts.isEmpty()) {
            return;
        }

        syncCountsToDb(likeCounts, "post like",
                (postId, count) -> communityPostRepository.setLikeCount(postId, count),
                redisService::deletePostLikeKeys);

        log.info("Post like count sync completed - processed: {}", likeCounts.size());
    }

    /**
     * 댓글 좋아요 이벤트 동기화 (Redis Queue → DB)
     */
    @Scheduled(fixedDelayString = "${community.scheduler.like-sync-delay:300000}")
    public void syncCommentLikeEvents() {
        List<LikeEvent> events = redisService.pollCommentLikeEvents(1000);

        if (events.isEmpty()) {
            return;
        }

        int[] counts = syncLikeEvents(events,
                commentLikeRepository::insertCommentLike,
                commentLikeRepository::deleteByCommentIdAndUserId);

        redisService.trimCommentLikeEvents(events.size());

        log.info("Comment like events synced - total: {}, added: {}, removed: {}",
                events.size(), counts[0], counts[1]);
    }

    /**
     * 댓글 좋아요 개수 동기화 (Redis → DB)
     */
    @Scheduled(fixedDelayString = "${community.scheduler.like-sync-delay:300000}", initialDelayString = "${community.scheduler.like-sync-delay:320000}")
    public void syncCommentLikeCounts() {
        Map<Long, Integer> likeCounts = redisService.scanAndCollectCommentLikeCounts();

        if (likeCounts.isEmpty()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            likeCounts.forEach((commentId, likeCount) -> {
                try {
                    int affected = commentRepository.setLikeCount(commentId, likeCount);
                    if (affected == 0) {
                        redisService.deleteCommentLikeKeys(commentId);
                    }
                } catch (Exception e) {
                    log.error("Failed to sync comment like count: {}", commentId, e);
                }
            });
        });

        log.info("Comment like count sync completed - processed: {}", likeCounts.size());
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================

    private void syncCountsToDb(Map<Long, Integer> counts, String type,
            CountUpdater updater, KeyDeleter deleter) {
        transactionTemplate.executeWithoutResult(status -> {
            counts.forEach((id, count) -> {
                try {
                    int affected = updater.update(id, count);
                    if (affected > 0) {
                        log.debug("{} count synced - id: {}, count: {}", type, id, count);
                    } else {
                        deleter.delete(id);
                    }
                } catch (Exception e) {
                    log.error("Failed to sync {} count for id: {}", type, id, e);
                }
            });
        });
    }

    private int[] syncLikeEvents(List<LikeEvent> events,
            LikeInserter inserter, LikeDeleter deleter) {
        Map<String, LikeEvent> deduplicated = events.stream()
                .collect(Collectors.toMap(
                        e -> e.targetId() + ":" + e.userId(),
                        e -> e,
                        (old, newer) -> newer));

        int[] counts = { 0, 0 };
        transactionTemplate.executeWithoutResult(status -> {
            deduplicated.values().forEach(event -> {
                try {
                    if (event.isAdd()) {
                        inserter.insert(event.targetId(), event.userId());
                        counts[0]++;
                    } else {
                        deleter.delete(event.targetId(), event.userId());
                        counts[1]++;
                    }
                } catch (Exception e) {
                    log.error("Failed to sync like event: {}", event, e);
                }
            });
        });
        return counts;
    }

    @FunctionalInterface
    private interface CountUpdater {
        int update(Long id, Integer count);
    }

    @FunctionalInterface
    private interface KeyDeleter {
        void delete(Long id);
    }

    @FunctionalInterface
    private interface LikeInserter {
        void insert(Long targetId, Long userId);
    }

    @FunctionalInterface
    private interface LikeDeleter {
        void delete(Long targetId, Long userId);
    }
}