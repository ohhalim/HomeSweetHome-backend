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

/**
 * [커뮤니티 카운터 서비스 - 조회수, 좋아요수, 댓글수 관리]
 *
 * [이 서비스가 하는 일]
 * 1. 조회수 증가/조회
 * 2. 게시글/댓글 좋아요 토글 (누르면 추가, 다시 누르면 취소)
 * 3. 댓글수 증가/감소/조회
 *
 * [왜 Redis를 사용해?]
 * - 조회수, 좋아요는 자주 바뀌는 데이터야
 * - 매번 DB에 저장하면 느리고 부하가 커
 * - Redis는 메모리 기반이라 엄청 빨라! (1초에 수만 건 처리 가능)
 *
 * [Cache Miss란?]
 * 캐시에서 데이터를 찾았는데 없는 상황.
 * 이때는 DB에서 가져와서 캐시에 저장해둬 (Lazy Loading)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCountService {

    // 게시글 DB 접근용
    private final CommunityPostRepository postRepository;
    // 게시글 좋아요 DB 접근용
    private final CommunityPostLikeRepository postLikeRepository;
    // 댓글 좋아요 DB 접근용
    private final CommunityCommentLikeRepository commentLikeRepository;
    // Redis 접근용 (실제 카운터 저장/조회)
    private final CommunityRedisService redisService;

    // ============================================================
    // [조회수 관련]
    // ============================================================

    /**
     * [조회수 증가]
     *
     * [동작 흐름]
     * 1. Redis에서 조회수 +1
     * 2. 만약 Redis에 키가 없으면 (Cache Miss)
     * -> DB에서 현재 조회수 가져와서 Redis에 저장
     * -> 그 다음 +1
     */
    @Transactional
    public void increaseViewCount(Long postId) {
        // Redis에서 조회수 +1 시도
        Long result = redisService.incrementPostViewCount(postId);

        // result가 -1이면 = Redis에 키가 없었음 (Cache Miss)
        if (result == -1) {
            // DB에서 현재 조회수 가져와서 Redis에 저장
            initViewCountFromDB(postId);
            // 다시 +1
            redisService.incrementPostViewCount(postId);
        }

        log.debug("View count increased - postId: {}", postId);
    }

    /**
     * [DB에서 조회수를 Redis에 로드]
     * Cache Miss 발생 시 호출됨
     */
    public void initViewCountFromDB(Long postId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        // DB의 현재 조회수를 Redis에 저장
        redisService.setPostViewCount(postId, post.getViewCount());
    }

    // ============================================================
    // [댓글수 관련]
    // ============================================================

    /**
     * [댓글수 증가]
     * 댓글 작성 시 호출
     */
    @Transactional
    public void increaseCommentCount(Long postId) {
        Long result = redisService.incrementPostCommentCount(postId);

        if (result == -1) {
            initCommentCountFromDB(postId);
            redisService.incrementPostCommentCount(postId);
        }
        log.debug("Comment count increased - postId: {}", postId);
    }

    /**
     * [댓글수 감소]
     * 댓글 삭제 시 호출
     */
    @Transactional
    public void decreaseCommentCount(Long postId) {
        Long result = redisService.decreasePostCommentCount(postId);

        if (result == -1) {
            initCommentCountFromDB(postId);
            redisService.decreasePostCommentCount(postId);
        }
        log.debug("Comment count decreased - postId: {}", postId);
    }

    /**
     * [DB에서 댓글수를 Redis에 로드]
     */
    private void initCommentCountFromDB(Long postId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        redisService.setPostCommentCount(postId, post.getCommentCount());
    }

    // ============================================================
    // [게시글 좋아요 관련]
    // ============================================================

    /**
     * [게시글 좋아요 토글]
     *
     * [토글이란?]
     * - 처음 누르면 좋아요 추가
     * - 다시 누르면 좋아요 취소
     * - 전등 스위치처럼 ON/OFF 전환!
     *
     * [동작 흐름]
     * 1. Redis Set에서 유저ID 추가/제거
     * 2. 이벤트 큐에 기록 (나중에 스케줄러가 DB에 동기화)
     */
    @Transactional
    public void togglePostLike(Long postId, Long userId) {
        // Redis에서 좋아요 토글 시도
        Long result = redisService.togglePostLike(postId, userId);

        // Cache Miss면 DB에서 로드 후 다시 토글
        if (result == -1) {
            initPostLikesFromDB(postId);
            result = redisService.togglePostLike(postId, userId);
        }

        // result가 1이면 추가됨, 0이면 제거됨
        boolean isAdded = (result == 1);

        // 이벤트 큐에 기록 (스케줄러가 나중에 DB에 저장함)
        redisService.addPostLikeEvent(postId, userId, isAdded);

        log.debug("Post like toggled - postId: {}, userId: {}, action: {}",
                postId, userId, isAdded ? "ADDED" : "REMOVED");
    }

    /**
     * [게시글 좋아요 상태 확인]
     * 현재 사용자가 이 게시글에 좋아요를 눌렀는지 확인
     *
     * @return true = 좋아요 누른 상태, false = 안 누른 상태
     */
    public boolean isPostLiked(Long postId, Long userId) {
        // Redis에 키가 없으면 DB에서 로드
        if (!redisService.hasPostLikeKey(postId)) {
            initPostLikesFromDB(postId);
        }
        // Redis Set에 이 유저가 있는지 확인
        return redisService.isPostLiked(postId, userId);
    }

    /**
     * [DB에서 게시글 좋아요 목록을 Redis에 로드]
     * 해당 게시글에 좋아요 누른 유저 ID 목록을 가져와서 Redis Set에 저장
     */
    private void initPostLikesFromDB(Long postId) {
        List<Long> userIds = postLikeRepository.findUserIdsByPostId(postId);
        redisService.setPostLikes(postId, userIds);
        log.info("Loaded post likes from DB - postId: {}, count: {}", postId, userIds.size());
    }

    // ============================================================
    // [댓글 좋아요 관련]
    // ============================================================

    /**
     * [댓글 좋아요 토글]
     * 게시글 좋아요와 같은 로직
     */
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
    }

    /**
     * [댓글 좋아요 상태 확인]
     */
    public boolean isCommentLiked(Long commentId, Long userId) {
        if (!redisService.hasCommentLikeKey(commentId)) {
            initCommentLikesFromDB(commentId);
        }
        return redisService.isCommentLiked(commentId, userId);
    }

    /**
     * [DB에서 댓글 좋아요 목록을 Redis에 로드]
     */
    private void initCommentLikesFromDB(Long commentId) {
        List<Long> userIds = commentLikeRepository.findUserIdsByCommentId(commentId);
        redisService.setCommentLikes(commentId, userIds);
        log.info("Loaded comment likes from DB - commentId: {}, count: {}", commentId, userIds.size());
    }

    // ============================================================
    // [카운터 조회 - 단건]
    // ============================================================

    /**
     * [Redis에서 조회수 가져오기]
     * Cache Miss면 DB에서 로드
     */
    public Integer getViewCountFromCache(Long postId) {
        Integer viewCount = redisService.getPostViewCount(postId);
        if (viewCount != null)
            return viewCount;
        if (viewCount != null)
            return viewCount;

        // Cache Miss -> DB에서 로드
        initViewCountFromDB(postId);
        return redisService.getPostViewCount(postId);
    }

    /**
     * [Redis에서 좋아요수 가져오기]
     * 좋아요수 = Redis Set의 크기
     */
    public Integer getLikeCountFromCache(Long postId) {
        Integer likeCount = redisService.getPostLikeCount(postId);
        if (likeCount != null)
            return likeCount;
        if (likeCount != null)
            return likeCount;

        initPostLikesFromDB(postId);
        return redisService.getPostLikeCount(postId);
    }

    /**
     * [Redis에서 댓글수 가져오기]
     */
    public Integer getCommentCountFromCache(Long postId) {
        Integer commentCount = redisService.getPostCommentCount(postId);
        if (commentCount != null)
            return commentCount;
        if (commentCount != null)
            return commentCount;

        initCommentCountFromDB(postId);
        return redisService.getPostCommentCount(postId);
    }

    /**
     * [Redis에서 댓글 좋아요수 가져오기]
     */
    public Integer getCommentLikeCountFromCache(Long commentId) {
        Integer likeCount = redisService.getCommentLikeCount(commentId);
        if (likeCount != null)
            return likeCount;
        if (likeCount != null)
            return likeCount;

        initCommentLikesFromDB(commentId);
        return redisService.getCommentLikeCount(commentId);
    }

    // ============================================================
    // [카운터 조회 - 벌크 (여러 개 한번에)]
    // N+1 문제 방지: 게시글 10개 조회할 때 카운터 조회도 10번 -> 1번으로!
    // ============================================================

    /**
     * [여러 댓글의 좋아요수 한 번에 조회]
     *
     * @param commentIds 댓글 ID 리스트
     * @return Map<댓글ID, 좋아요수>
     */
    public Map<Long, Integer> getBulkCommentLikeCountsFromCache(List<Long> commentIds) {
        // Redis MGET으로 한 번에 조회
        Map<Long, Integer> result = redisService.getBulkCommentLikeCounts(commentIds);

        // Cache Miss인 것들만 개별 처리
        for (Long commentId : commentIds) {
            if (result.get(commentId) == null) {
                initCommentLikesFromDB(commentId);
                result.put(commentId, redisService.getCommentLikeCount(commentId));
            }
        }
        return result;
    }

    /**
     * [게시글의 모든 카운터(조회수, 좋아요수, 댓글수) 한 번에 조회]
     */
    public PostCounts getPostCounts(Long postId) {
        return PostCounts.ofNullSafe(
                getViewCountFromCache(postId),
                getLikeCountFromCache(postId),
                getCommentCountFromCache(postId));
    }

    /**
     * [여러 게시글의 모든 카운터 한 번에 조회]
     * 게시글 목록 조회 시 사용 (N+1 방지)
     *
     * @param postIds 게시글 ID 리스트
     * @return Map<게시글ID, 카운터들>
     */
    public Map<Long, PostCounts> getBulkPostCounts(List<Long> postIds) {
        // 각 카운터를 벌크로 조회
        Map<Long, Integer> viewCounts = getBulkViewCountsFromCache(postIds);
        Map<Long, Integer> likeCounts = getBulkLikeCountsFromCache(postIds);
        Map<Long, Integer> commentCounts = getBulkCommentCountsFromCache(postIds);

        // 게시글별로 카운터들 합치기
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
    // [벌크 조회 - 내부 메서드]
    // Redis MGET 사용해서 여러 키를 한 번에 조회
    // ============================================================

    /**
     * [여러 게시글의 조회수 한 번에 조회]
     */
    public Map<Long, Integer> getBulkViewCountsFromCache(List<Long> postIds) {
        Map<Long, Integer> result = redisService.getBulkViewCounts(postIds);

        // Cache Miss 처리
        for (Long postId : postIds) {
            if (result.get(postId) == null) {
                initViewCountFromDB(postId);
                result.put(postId, redisService.getPostViewCount(postId));
            }
        }
        return result;
    }

    /**
     * [여러 게시글의 좋아요수 한 번에 조회]
     */
    public Map<Long, Integer> getBulkLikeCountsFromCache(List<Long> postIds) {
        Map<Long, Integer> result = redisService.getBulkLikeCounts(postIds);

        for (Long postId : postIds) {
            if (result.get(postId) == null) {
                initPostLikesFromDB(postId);
                result.put(postId, redisService.getPostLikeCount(postId));
            }
        }
        return result;
    }

    /**
     * [여러 게시글의 댓글수 한 번에 조회]
     */
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