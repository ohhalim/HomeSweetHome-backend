package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.repository.CommentVoteRepository;
import com.homesweet.homesweetback.domain.community.repository.PostVoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Reddit-style 카르마 시스템
 *
 * 카르마(Karma): 사용자 신뢰도 점수
 * - 게시글/댓글이 받은 Upvote/Downvote 기반 계산
 * - Upvote: +1 카르마
 * - Downvote: -1 카르마
 * - Post Karma와 Comment Karma 분리 집계
 *
 * @author ohhalim777@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KarmaService {

    private final PostVoteRepository postVoteRepository;
    private final CommentVoteRepository commentVoteRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String KARMA_KEY_PREFIX = "karma:user:";
    private static final String POST_KARMA_SUFFIX = ":post";
    private static final String COMMENT_KARMA_SUFFIX = ":comment";
    private static final long KARMA_CACHE_TTL = 1; // 1 hour

    /**
     * 총 카르마 조회 (Post + Comment)
     */
    @Cacheable(value = "karmaCache", key = "'total:' + #userId", unless = "#result < 0")
    public long getTotalKarma(Long userId) {
        return getPostKarma(userId) + getCommentKarma(userId);
    }

    /**
     * Post 카르마 조회
     */
    @Cacheable(value = "karmaCache", key = "'post:' + #userId", unless = "#result < 0")
    public long getPostKarma(Long userId) {
        // Redis 캐시 확인
        String cacheKey = KARMA_KEY_PREFIX + userId + POST_KARMA_SUFFIX;
        String cachedKarma = redisTemplate.opsForValue().get(cacheKey);

        if (cachedKarma != null) {
            return Long.parseLong(cachedKarma);
        }

        // DB에서 계산
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        long upvotes = postVoteRepository.countUpvotesByAuthor(user);
        long downvotes = postVoteRepository.countDownvotesByAuthor(user);
        long karma = upvotes - downvotes;

        // Redis 캐시 저장
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(karma), KARMA_CACHE_TTL, TimeUnit.HOURS);

        return karma;
    }

    /**
     * Comment 카르마 조회
     */
    @Cacheable(value = "karmaCache", key = "'comment:' + #userId", unless = "#result < 0")
    public long getCommentKarma(Long userId) {
        // Redis 캐시 확인
        String cacheKey = KARMA_KEY_PREFIX + userId + COMMENT_KARMA_SUFFIX;
        String cachedKarma = redisTemplate.opsForValue().get(cacheKey);

        if (cachedKarma != null) {
            return Long.parseLong(cachedKarma);
        }

        // DB에서 계산
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        long upvotes = commentVoteRepository.countUpvotesByAuthor(user);
        long downvotes = commentVoteRepository.countDownvotesByAuthor(user);
        long karma = upvotes - downvotes;

        // Redis 캐시 저장
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(karma), KARMA_CACHE_TTL, TimeUnit.HOURS);

        return karma;
    }

    /**
     * 카르마 상세 정보 조회
     */
    public KarmaDetail getKarmaDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        // Post 투표 정보
        long postUpvotes = postVoteRepository.countUpvotesByAuthor(user);
        long postDownvotes = postVoteRepository.countDownvotesByAuthor(user);
        long postKarma = postUpvotes - postDownvotes;

        // Comment 투표 정보
        long commentUpvotes = commentVoteRepository.countUpvotesByAuthor(user);
        long commentDownvotes = commentVoteRepository.countDownvotesByAuthor(user);
        long commentKarma = commentUpvotes - commentDownvotes;

        return KarmaDetail.builder()
                .userId(userId)
                .postKarma(postKarma)
                .postUpvotes(postUpvotes)
                .postDownvotes(postDownvotes)
                .commentKarma(commentKarma)
                .commentUpvotes(commentUpvotes)
                .commentDownvotes(commentDownvotes)
                .totalKarma(postKarma + commentKarma)
                .build();
    }

    /**
     * 카르마 레벨 계산
     *
     * Reddit-style 레벨 시스템:
     * - 0-99: Newbie
     * - 100-499: Regular
     * - 500-1999: Contributor
     * - 2000-4999: Veteran
     * - 5000-9999: Expert
     * - 10000+: Legend
     */
    public KarmaLevel getKarmaLevel(Long userId) {
        long totalKarma = getTotalKarma(userId);

        if (totalKarma < 0) return KarmaLevel.NEGATIVE;
        if (totalKarma < 100) return KarmaLevel.NEWBIE;
        if (totalKarma < 500) return KarmaLevel.REGULAR;
        if (totalKarma < 2000) return KarmaLevel.CONTRIBUTOR;
        if (totalKarma < 5000) return KarmaLevel.VETERAN;
        if (totalKarma < 10000) return KarmaLevel.EXPERT;
        return KarmaLevel.LEGEND;
    }

    /**
     * 카르마 캐시 무효화
     */
    @Transactional
    public void invalidateKarmaCache(Long userId) {
        String postKarmaKey = KARMA_KEY_PREFIX + userId + POST_KARMA_SUFFIX;
        String commentKarmaKey = KARMA_KEY_PREFIX + userId + COMMENT_KARMA_SUFFIX;

        redisTemplate.delete(postKarmaKey);
        redisTemplate.delete(commentKarmaKey);

        log.debug("Karma cache invalidated for user: {}", userId);
    }

    /**
     * 카르마 캐시 전체 갱신 (스케줄러)
     *
     * 매시간 전체 사용자 카르마를 재계산하여 정확도 유지
     */
    @Scheduled(fixedDelay = 3600000) // 1시간
    @Transactional
    public void refreshAllKarmaCache() {
        log.info("Starting karma cache refresh for all users");

        // 모든 사용자의 카르마 재계산
        // 실제 프로덕션에서는 배치 처리로 분할 필요
        userRepository.findAll().forEach(user -> {
            try {
                // Post 카르마 재계산
                long postUpvotes = postVoteRepository.countUpvotesByAuthor(user);
                long postDownvotes = postVoteRepository.countDownvotesByAuthor(user);
                long postKarma = postUpvotes - postDownvotes;

                // Comment 카르마 재계산
                long commentUpvotes = commentVoteRepository.countUpvotesByAuthor(user);
                long commentDownvotes = commentVoteRepository.countDownvotesByAuthor(user);
                long commentKarma = commentUpvotes - commentDownvotes;

                // Redis 캐시 업데이트
                String postKarmaKey = KARMA_KEY_PREFIX + user.getId() + POST_KARMA_SUFFIX;
                String commentKarmaKey = KARMA_KEY_PREFIX + user.getId() + COMMENT_KARMA_SUFFIX;

                redisTemplate.opsForValue().set(postKarmaKey, String.valueOf(postKarma), KARMA_CACHE_TTL, TimeUnit.HOURS);
                redisTemplate.opsForValue().set(commentKarmaKey, String.valueOf(commentKarma), KARMA_CACHE_TTL, TimeUnit.HOURS);
            } catch (Exception e) {
                log.error("Failed to refresh karma for user: {}", user.getId(), e);
            }
        });

        log.info("Karma cache refresh completed");
    }

    /**
     * 카르마 상세 정보 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class KarmaDetail {
        private Long userId;
        private Long postKarma;
        private Long postUpvotes;
        private Long postDownvotes;
        private Long commentKarma;
        private Long commentUpvotes;
        private Long commentDownvotes;
        private Long totalKarma;
    }

    /**
     * 카르마 레벨
     */
    public enum KarmaLevel {
        NEGATIVE("음수 카르마"),
        NEWBIE("새내기", 0, 99),
        REGULAR("일반", 100, 499),
        CONTRIBUTOR("기여자", 500, 1999),
        VETERAN("베테랑", 2000, 4999),
        EXPERT("전문가", 5000, 9999),
        LEGEND("전설", 10000, Long.MAX_VALUE);

        private final String displayName;
        private final long minKarma;
        private final long maxKarma;

        KarmaLevel(String displayName) {
            this(displayName, Long.MIN_VALUE, -1);
        }

        KarmaLevel(String displayName, long minKarma, long maxKarma) {
            this.displayName = displayName;
            this.minKarma = minKarma;
            this.maxKarma = maxKarma;
        }

        public String getDisplayName() {
            return displayName;
        }

        public long getMinKarma() {
            return minKarma;
        }

        public long getMaxKarma() {
            return maxKarma;
        }
    }
}
