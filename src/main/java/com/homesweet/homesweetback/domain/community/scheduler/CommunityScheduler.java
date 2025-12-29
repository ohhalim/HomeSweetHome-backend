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
     * 인기 게시글 캐시 워밍업 - 서버 시작 시 및 1시간마다 실행
     * 최근 게시글 상위 100개의 좋아요/조회수 데이터를 Redis에 미리 로딩
     */
    @Scheduled(initialDelay = 5000, fixedDelay = 3600000)  // 5초 후 시작, 1시간마다
    public void warmupPopularPostsCache() {
        log.info("Starting cache warmup for popular posts...");

        try {
            // 최근 게시글 100개 조회 (가장 많이 조회될 가능성 높음)
            var recentPosts = communityPostRepository.findByIsDeletedFalse(
                    PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "postId"))
            );

            int warmupCount = 0;
            for (var post : recentPosts.getContent()) {
                Long postId = post.getPostId();

                // 좋아요 데이터 워밍업 (Cache Miss 방지)
                if (!redisService.hasPostLikeKey(postId)) {
                    List<Long> userIds = postLikeRepository.findUserIdsByPostId(postId);
                    redisService.setPostLikes(postId, userIds);
                    warmupCount++;
                }

                // 조회수/댓글수/좋아요수 워밍업
                communityCountService.getViewCountFromCache(postId);
                communityCountService.getLikeCountFromCache(postId);
                communityCountService.getCommentCountFromCache(postId);
            }

            log.info("Cache warmup completed - posts processed: {}, likes warmed up: {}",
                    recentPosts.getContent().size(), warmupCount);

        } catch (Exception e) {
            log.error("Failed to warmup cache", e);
        }
    }

    @Scheduled(initialDelay = 100000, fixedDelay = 110000)
    public void updateCountData() {
        // 1. Redis에서 조회수 데이터 수집
        Map<Long, Integer> viewCounts = redisService.scanAndCollectViewCounts();

        if (viewCounts.isEmpty()) {
            return;
        }

        // 2. DB 업데이트 (트랜잭션)
        transactionTemplate.executeWithoutResult(status -> {
            viewCounts.forEach((postId, viewCount) -> {
                try {
                    int affected = communityPostRepository.updateViewCount(postId, viewCount);

                    if (affected > 0) {
                        // DB 동기화 성공 시 Redis 값 유지 (Cache-Aside 패턴)
                        log.debug("View count synced to DB - postId: {}, viewCount: {}", postId, viewCount);
                    } else {
                        // 게시글이 삭제된 경우 Redis 키 정리
                        redisService.deletePostViewKey(postId);
                    }
                } catch (Exception e) {
                    log.error("Failed to update view count for postId: {}", postId, e);
                }
            });
        });

        log.info("View count sync completed - total processed: {}", viewCounts.size());
    }

    // 댓글 수정
    @Scheduled(initialDelay = 200000, fixedDelay = 210000)
    public void updateCommentData() {
        // 1. Redis에서 댓글수 데이터 수집
        Map<Long, Integer> commentCounts = redisService.scanAndCollectCommentCounts();

        if (commentCounts.isEmpty()) {
            return;
        }

        // 2. DB 업데이트 (트랜잭션)
        transactionTemplate.executeWithoutResult(status -> {
            commentCounts.forEach((postId, commentCount) -> {
                try {
                    int affected = communityPostRepository.setCommentCount(postId, commentCount);

                    if (affected > 0) {
                        // DB 동기화 성공 시 Redis 값 유지 (Cache-Aside 패턴)
                        log.debug("Comment count synced to DB - postId: {}, commentCount: {}", postId, commentCount);
                    } else {
                        // 게시글이 삭제된 경우 Redis 키 정리
                        redisService.deletePostCommentCountKey(postId);
                    }
                } catch (Exception e) {
                    log.error("Failed to update comment count for postId: {}", postId, e);
                }
            });
        });

        log.info("Comment count sync completed - total processed: {}", commentCounts.size());
    }

    /**
     * 게시글 좋아요 Event Queue 배치 동기화 (관계 + 개수)
     * 5분마다 실행
     */
    @Scheduled(fixedDelay = 300000)  // 5분
    public void syncPostLikeEvents() {
        // 1. Redis Queue에서 이벤트 가져오기
        List<LikeEvent> events = redisService.pollPostLikeEvents(1000);

        if (events.isEmpty()) {
            return;
        }

        // 2. 마지막 상태만 추출 (중복 제거)
        Map<String, LikeEvent> deduplicated = events.stream()
                .collect(Collectors.toMap(
                        e -> e.targetId() + ":" + e.userId(),
                        e -> e,
                        (old, new_) -> new_  // 마지막 이벤트만 유지
                ));

        // 3. DB 업데이트 (트랜잭션)
        int[] counts = {0, 0};  // [addCount, removeCount]
        transactionTemplate.executeWithoutResult(status -> {
            deduplicated.values().forEach(event -> {
                try {
                    if (event.isAdd()) {
                        postLikeRepository.insertPostLike(event.targetId(), event.userId());
                        counts[0]++;
                    } else {
                        postLikeRepository.deleteByPostIdAndUserId(event.targetId(), event.userId());
                        counts[1]++;
                    }
                } catch (Exception e) {
                    log.error("Failed to sync post like event: {}", event, e);
                }
            });
        });

        // 4. 처리된 이벤트 제거
        redisService.trimPostLikeEvents(events.size());

        log.info("Post like events synced - total: {}, added: {}, removed: {}",
                events.size(), counts[0], counts[1]);
    }

    /**
     * 게시글 좋아요 개수 배치 동기화 (Redis → DB)
     * 5분마다 실행 (좋아요 이벤트 동기화 직후)
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 310000)  // 5분, 초기 지연 5분 10초
    public void syncPostLikeCount() {
        // 1. Redis에서 좋아요수 데이터 수집
        Map<Long, Integer> likeCounts = redisService.scanAndCollectPostLikeCounts();

        if (likeCounts.isEmpty()) {
            return;
        }

        // 2. DB 업데이트 (트랜잭션)
        transactionTemplate.executeWithoutResult(status -> {
            likeCounts.forEach((postId, likeCount) -> {
                try {
                    int affected = communityPostRepository.setLikeCount(postId, likeCount);

                    if (affected > 0) {
                        // DB 동기화 성공 시 Redis 값 유지 (Cache-Aside 패턴)
                        log.debug("Post like count synced to DB - postId: {}, likeCount: {}", postId, likeCount);
                    } else {
                        // 게시글이 삭제된 경우 Redis 키 정리
                        redisService.deletePostLikeKeys(postId);
                    }
                } catch (Exception e) {
                    log.error("Failed to update like count for postId: {}", postId, e);
                }
            });
        });

        log.info("Post like count sync completed - total processed: {}", likeCounts.size());
    }

    /**
     * 댓글 좋아요 Event Queue 배치 동기화 (관계)
     * 5분마다 실행
     */
    @Scheduled(fixedDelay = 300000)  // 5분
    public void syncCommentLikeEvents() {
        // 1. Redis Queue에서 이벤트 가져오기
        List<LikeEvent> events = redisService.pollCommentLikeEvents(1000);

        if (events.isEmpty()) {
            return;
        }

        // 2. 마지막 상태만 추출 (중복 제거)
        Map<String, LikeEvent> deduplicated = events.stream()
                .collect(Collectors.toMap(
                        e -> e.targetId() + ":" + e.userId(),
                        e -> e,
                        (old, new_) -> new_
                ));

        // 3. DB 업데이트 (트랜잭션)
        int[] counts = {0, 0};  // [addCount, removeCount]
        transactionTemplate.executeWithoutResult(status -> {
            deduplicated.values().forEach(event -> {
                try {
                    if (event.isAdd()) {
                        commentLikeRepository.insertCommentLike(event.targetId(), event.userId());
                        counts[0]++;
                    } else {
                        commentLikeRepository.deleteByCommentIdAndUserId(event.targetId(), event.userId());
                        counts[1]++;
                    }
                } catch (Exception e) {
                    log.error("Failed to sync comment like event: {}", event, e);
                }
            });
        });

        // 4. 처리된 이벤트 제거
        redisService.trimCommentLikeEvents(events.size());

        log.info("Comment like events synced - total: {}, added: {}, removed: {}",
                events.size(), counts[0], counts[1]);
    }

    /**
     * 댓글 좋아요 개수 배치 동기화 (Redis → DB)
     * 5분마다 실행 (댓글 좋아요 이벤트 동기화 직후)
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 320000)  // 5분, 초기 지연 5분 20초
    public void syncCommentLikeCount() {
        // 1. Redis에서 댓글 좋아요수 데이터 수집
        Map<Long, Integer> likeCounts = redisService.scanAndCollectCommentLikeCounts();

        if (likeCounts.isEmpty()) {
            return;
        }

        // 2. DB 업데이트 (트랜잭션)
        transactionTemplate.executeWithoutResult(status -> {
            likeCounts.forEach((commentId, likeCount) -> {
                try {
                    int affected = commentRepository.setLikeCount(commentId, likeCount);

                    if (affected > 0) {
                        // DB 동기화 성공 시 Redis 값 유지 (Cache-Aside 패턴)
                        log.debug("Comment like count synced to DB - commentId: {}, likeCount: {}", commentId, likeCount);
                    } else {
                        // 댓글이 삭제된 경우 Redis 키 정리
                        redisService.deleteCommentLikeKeys(commentId);
                    }
                } catch (Exception e) {
                    log.error("Failed to update comment like count for commentId: {}", commentId, e);
                }
            });
        });

        log.info("Comment like count sync completed - total processed: {}", likeCounts.size());
    }
}