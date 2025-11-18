package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reddit-style 게시글 정렬 서비스
 *
 * 정렬 알고리즘:
 * 1. HOT: 시간 가중치 + 점수 (최근 인기글)
 * 2. NEW: 최신 순
 * 3. TOP: 점수 높은 순 (기간별 필터 가능)
 * 4. CONTROVERSIAL: 논쟁적인 글 (upvote와 downvote 비율이 비슷한 글)
 *
 * @author ohhalim777@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostSortingService {

    private final CommunityPostRepository postRepository;

    /**
     * HOT 알고리즘 정렬
     *
     * Reddit의 Hot 알고리즘:
     * hot_score = log10(max(|score|, 1)) * sign(score) + (created_time / 45000)
     *
     * - 점수가 높을수록 순위 상승
     * - 시간이 최근일수록 순위 상승
     * - 45000초(12.5시간)마다 점수 1점과 동등한 가치
     */
    public Page<CommunityPostEntity> getHotPosts(Pageable pageable, Long subredditId) {
        List<CommunityPostEntity> posts = subredditId != null ?
            postRepository.findBySubreddit_SubredditIdAndIsDeletedFalse(subredditId) :
            postRepository.findByIsDeletedFalse();

        List<CommunityPostEntity> sortedPosts = posts.stream()
                .sorted(Comparator.comparingDouble(this::calculateHotScore).reversed())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toList());

        return new PageImpl<>(sortedPosts, pageable, posts.size());
    }

    /**
     * HOT 점수 계산
     */
    private double calculateHotScore(CommunityPostEntity post) {
        int score = post.getScore();
        long secondsSinceEpoch = post.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond();

        // score의 절댓값이 0이면 1로 설정 (log10(0) 방지)
        double absScore = Math.max(Math.abs(score), 1);

        // score의 부호 (-1, 0, 1)
        int sign = Integer.compare(score, 0);

        // Hot score 계산
        double order = Math.log10(absScore);
        double timeFactor = secondsSinceEpoch / 45000.0;

        return order * sign + timeFactor;
    }

    /**
     * TOP 알고리즘 정렬
     *
     * 단순히 score(upvote - downvote) 순으로 정렬
     * 기간 필터링 가능 (24시간, 1주일, 1개월, 전체)
     */
    public Page<CommunityPostEntity> getTopPosts(Pageable pageable, Long subredditId, TopPeriod period) {
        LocalDateTime since = getTimeSince(period);

        List<CommunityPostEntity> posts = subredditId != null ?
            postRepository.findBySubreddit_SubredditIdAndIsDeletedFalseAndCreatedAtAfter(subredditId, since) :
            postRepository.findByIsDeletedFalseAndCreatedAtAfter(since);

        List<CommunityPostEntity> sortedPosts = posts.stream()
                .sorted(Comparator.comparingInt(CommunityPostEntity::getScore).reversed())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toList());

        return new PageImpl<>(sortedPosts, pageable, posts.size());
    }

    /**
     * NEW 알고리즘 정렬
     *
     * 생성 시간 기준 최신 순
     */
    public Page<CommunityPostEntity> getNewPosts(Pageable pageable, Long subredditId) {
        return subredditId != null ?
            postRepository.findBySubreddit_SubredditIdAndIsDeletedFalseOrderByCreatedAtDesc(subredditId, pageable) :
            postRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
    }

    /**
     * CONTROVERSIAL 알고리즘 정렬
     *
     * 논쟁적인 글: upvote와 downvote가 많지만 비율이 비슷한 글
     * controversial_score = min(upvotes, downvotes) / max(upvotes, downvotes)
     *
     * - 비율이 1에 가까울수록 논쟁적
     * - 총 투표 수가 많을수록 우선순위 상승
     */
    public Page<CommunityPostEntity> getControversialPosts(Pageable pageable, Long subredditId, TopPeriod period) {
        LocalDateTime since = getTimeSince(period);

        List<CommunityPostEntity> posts = subredditId != null ?
            postRepository.findBySubreddit_SubredditIdAndIsDeletedFalseAndCreatedAtAfter(subredditId, since) :
            postRepository.findByIsDeletedFalseAndCreatedAtAfter(since);

        List<CommunityPostEntity> sortedPosts = posts.stream()
                .filter(post -> post.getUpvoteCount() > 0 && post.getDownvoteCount() > 0) // 양쪽 투표가 있어야 논쟁적
                .sorted(Comparator.comparingDouble(this::calculateControversialScore).reversed())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toList());

        return new PageImpl<>(sortedPosts, pageable, posts.size());
    }

    /**
     * Controversial 점수 계산
     */
    private double calculateControversialScore(CommunityPostEntity post) {
        int upvotes = post.getUpvoteCount();
        int downvotes = post.getDownvoteCount();

        if (upvotes == 0 || downvotes == 0) {
            return 0.0;
        }

        // 더 작은 값 / 더 큰 값 (비율이 1에 가까울수록 논쟁적)
        double balance = (double) Math.min(upvotes, downvotes) / Math.max(upvotes, downvotes);

        // 총 투표 수 (많을수록 중요)
        int totalVotes = upvotes + downvotes;

        // 논쟁 점수 = 비율 * log(총 투표 수)
        return balance * Math.log10(Math.max(totalVotes, 1));
    }

    /**
     * RISING 알고리즘 정렬
     *
     * 최근에 빠르게 인기를 얻고 있는 글
     * - 최근 1-3시간 내 생성
     * - 급격한 점수 상승
     */
    public Page<CommunityPostEntity> getRisingPosts(Pageable pageable, Long subredditId) {
        LocalDateTime since = LocalDateTime.now().minus(3, ChronoUnit.HOURS);

        List<CommunityPostEntity> posts = subredditId != null ?
            postRepository.findBySubreddit_SubredditIdAndIsDeletedFalseAndCreatedAtAfter(subredditId, since) :
            postRepository.findByIsDeletedFalseAndCreatedAtAfter(since);

        List<CommunityPostEntity> sortedPosts = posts.stream()
                .sorted(Comparator.comparingDouble(this::calculateRisingScore).reversed())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toList());

        return new PageImpl<>(sortedPosts, pageable, posts.size());
    }

    /**
     * Rising 점수 계산
     */
    private double calculateRisingScore(CommunityPostEntity post) {
        long ageInMinutes = ChronoUnit.MINUTES.between(post.getCreatedAt(), LocalDateTime.now());

        if (ageInMinutes == 0) {
            ageInMinutes = 1; // 0으로 나누기 방지
        }

        int score = post.getScore();
        int commentCount = post.getCommentCount();

        // 점수 / 나이(분) + 댓글 보너스
        return ((double) score / ageInMinutes) + (commentCount * 0.1);
    }

    /**
     * 기간 계산
     */
    private LocalDateTime getTimeSince(TopPeriod period) {
        return switch (period) {
            case HOUR -> LocalDateTime.now().minus(1, ChronoUnit.HOURS);
            case DAY -> LocalDateTime.now().minus(1, ChronoUnit.DAYS);
            case WEEK -> LocalDateTime.now().minus(7, ChronoUnit.DAYS);
            case MONTH -> LocalDateTime.now().minus(30, ChronoUnit.DAYS);
            case YEAR -> LocalDateTime.now().minus(365, ChronoUnit.DAYS);
            case ALL_TIME -> LocalDateTime.of(2000, 1, 1, 0, 0); // 과거 날짜
        };
    }

    /**
     * TOP 조회 기간
     */
    public enum TopPeriod {
        HOUR,      // 1시간
        DAY,       // 24시간
        WEEK,      // 1주일
        MONTH,     // 1개월
        YEAR,      // 1년
        ALL_TIME   // 전체
    }
}
