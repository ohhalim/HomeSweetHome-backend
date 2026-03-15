package com.homesweet.homesweetback.domain.community.scheduler;

import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import com.homesweet.homesweetback.domain.community.service.CommunityCountService;
import com.homesweet.homesweetback.domain.community.service.CommunityRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * [커뮤니티 스케줄러 - Redis에서 DB로 조회수/댓글수 동기화]
 *
 * 좋아요는 DB 직접 처리이므로 동기화 불필요.
 * 조회수와 댓글수만 Redis -> DB 동기화.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommunityScheduler {

    private final CommunityRedisService redisService;
    private final CommunityPostRepository communityPostRepository;
    private final TransactionTemplate transactionTemplate;
    private final CommunityCountService communityCountService;

    /**
     * [캐시 워밍업 - 인기 게시글 조회수/댓글수 미리 준비]
     * 서버 시작 5초 후, 이후 1시간마다
     */
    @Scheduled(initialDelay = 5000, fixedDelayString = "${community.scheduler.warmup-delay:3600000}")
    public void warmupPopularPostsCache() {
        log.info("Starting cache warmup for popular posts...");

        try {
            var recentPosts = communityPostRepository.findByIsDeletedFalse(
                    PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "postId")));

            for (var post : recentPosts.getContent()) {
                communityCountService.getPostCounts(post.getPostId());
            }

            log.info("Cache warmup completed - posts: {}", recentPosts.getContent().size());
        } catch (Exception e) {
            log.error("Failed to warmup cache", e);
        }
    }

    /**
     * [조회수 동기화 - Redis -> DB]
     * 약 2분마다
     */
    @Scheduled(
            initialDelayString = "${community.scheduler.view-sync-delay:100000}",
            fixedDelayString = "${community.scheduler.view-sync-delay:110000}")
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
     * [댓글수 동기화 - Redis -> DB]
     * 약 3~4분마다
     */
    @Scheduled(
            initialDelayString = "${community.scheduler.comment-sync-delay:200000}",
            fixedDelayString = "${community.scheduler.comment-sync-delay:210000}")
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

    // ============================================================
    // [내부 헬퍼 메서드]
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

    @FunctionalInterface
    private interface CountUpdater {
        int update(Long id, Integer count);
    }

    @FunctionalInterface
    private interface KeyDeleter {
        void delete(Long id);
    }
}