package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.common.lock.DistributedLock;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.entity.*;
import com.homesweet.homesweetback.domain.community.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reddit-style 투표 서비스
 *
 * Upvote/Downvote 시스템
 * - 중복 투표 방지 (분산 락)
 * - 투표 변경 가능 (Upvote ↔ Downvote)
 * - 투표 취소 가능
 * - 점수 자동 계산 (score = upvotes - downvotes)
 *
 * @author ohhalim777@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteService {

    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final PostVoteRepository postVoteRepository;
    private final CommentVoteRepository commentVoteRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String POST_VOTE_KEY_PREFIX = "community:post:vote:";
    private static final String COMMENT_VOTE_KEY_PREFIX = "community:comment:vote:";
    private static final long VOTE_CACHE_TTL = 24; // 24 hours

    /**
     * 게시글 투표 토글
     *
     * 동작:
     * 1. 투표 없음 → 새 투표 생성
     * 2. 같은 투표 → 투표 취소
     * 3. 다른 투표 → 투표 변경
     */
    @Transactional
    @DistributedLock(key = "'community:post:vote:' + #postId + ':' + #userId", waitTime = 5, leaseTime = 3)
    @CacheEvict(value = "communityPostCache", key = "#postId")
    public void togglePostVote(Long postId, Long userId, PostVoteEntity.VoteType voteType) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        Optional<PostVoteEntity> existingVote = postVoteRepository.findByPostAndUser(post, user);

        String voteCountKey = POST_VOTE_KEY_PREFIX + postId;

        if (existingVote.isPresent()) {
            PostVoteEntity vote = existingVote.get();

            if (vote.getVoteType() == voteType) {
                // 같은 투표 클릭 → 투표 취소
                removePostVote(post, vote);
                log.debug("Post vote cancelled: post={}, user={}, type={}", postId, userId, voteType);
            } else {
                // 다른 투표 클릭 → 투표 변경
                changePostVote(post, vote, voteType);
                log.debug("Post vote changed: post={}, user={}, from={}, to={}",
                    postId, userId, vote.getVoteType(), voteType);
            }
        } else {
            // 새 투표 생성
            addPostVote(post, user, voteType);
            log.debug("Post vote added: post={}, user={}, type={}", postId, userId, voteType);
        }

        // Redis 캐시 업데이트
        updatePostVoteCache(postId, post);
    }

    /**
     * 댓글 투표 토글
     */
    @Transactional
    @DistributedLock(key = "'community:comment:vote:' + #commentId + ':' + #userId", waitTime = 5, leaseTime = 3)
    @CacheEvict(value = "communityCommentCache", key = "#commentId")
    public void toggleCommentVote(Long commentId, Long userId, CommentVoteEntity.VoteType voteType) {
        CommunityCommentEntity comment = commentRepository.findByCommentIdAndIsDeletedFalse(commentId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        Optional<CommentVoteEntity> existingVote = commentVoteRepository.findByCommentAndUser(comment, user);

        if (existingVote.isPresent()) {
            CommentVoteEntity vote = existingVote.get();

            if (vote.getVoteType() == voteType) {
                // 투표 취소
                removeCommentVote(comment, vote);
                log.debug("Comment vote cancelled: comment={}, user={}, type={}", commentId, userId, voteType);
            } else {
                // 투표 변경
                changeCommentVote(comment, vote, voteType);
                log.debug("Comment vote changed: comment={}, user={}, from={}, to={}",
                    commentId, userId, vote.getVoteType(), voteType);
            }
        } else {
            // 새 투표 생성
            addCommentVote(comment, user, voteType);
            log.debug("Comment vote added: comment={}, user={}, type={}", commentId, userId, voteType);
        }

        // Redis 캐시 업데이트
        updateCommentVoteCache(commentId, comment);
    }

    /**
     * 게시글 투표 추가
     */
    private void addPostVote(CommunityPostEntity post, User user, PostVoteEntity.VoteType voteType) {
        PostVoteEntity vote = PostVoteEntity.builder()
                .post(post)
                .user(user)
                .voteType(voteType)
                .build();
        postVoteRepository.save(vote);

        // 카운트 증가
        if (voteType == PostVoteEntity.VoteType.UPVOTE) {
            post.increaseUpvoteCount();
        } else {
            post.increaseDownvoteCount();
        }
    }

    /**
     * 게시글 투표 제거
     */
    private void removePostVote(CommunityPostEntity post, PostVoteEntity vote) {
        postVoteRepository.delete(vote);

        // 카운트 감소
        if (vote.getVoteType() == PostVoteEntity.VoteType.UPVOTE) {
            post.decreaseUpvoteCount();
        } else {
            post.decreaseDownvoteCount();
        }
    }

    /**
     * 게시글 투표 변경
     */
    private void changePostVote(CommunityPostEntity post, PostVoteEntity vote, PostVoteEntity.VoteType newVoteType) {
        PostVoteEntity.VoteType oldVoteType = vote.getVoteType();

        // 기존 투표 카운트 감소
        if (oldVoteType == PostVoteEntity.VoteType.UPVOTE) {
            post.decreaseUpvoteCount();
        } else {
            post.decreaseDownvoteCount();
        }

        // 새 투표 카운트 증가
        if (newVoteType == PostVoteEntity.VoteType.UPVOTE) {
            post.increaseUpvoteCount();
        } else {
            post.increaseDownvoteCount();
        }

        // 투표 타입 변경
        vote.changeVote(newVoteType);
    }

    /**
     * 댓글 투표 추가
     */
    private void addCommentVote(CommunityCommentEntity comment, User user, CommentVoteEntity.VoteType voteType) {
        CommentVoteEntity vote = CommentVoteEntity.builder()
                .comment(comment)
                .user(user)
                .voteType(voteType)
                .build();
        commentVoteRepository.save(vote);

        // 카운트 증가
        if (voteType == CommentVoteEntity.VoteType.UPVOTE) {
            comment.increaseUpvoteCount();
        } else {
            comment.increaseDownvoteCount();
        }
    }

    /**
     * 댓글 투표 제거
     */
    private void removeCommentVote(CommunityCommentEntity comment, CommentVoteEntity vote) {
        commentVoteRepository.delete(vote);

        // 카운트 감소
        if (vote.getVoteType() == CommentVoteEntity.VoteType.UPVOTE) {
            comment.decreaseUpvoteCount();
        } else {
            comment.decreaseDownvoteCount();
        }
    }

    /**
     * 댓글 투표 변경
     */
    private void changeCommentVote(CommunityCommentEntity comment, CommentVoteEntity vote,
                                   CommentVoteEntity.VoteType newVoteType) {
        CommentVoteEntity.VoteType oldVoteType = vote.getVoteType();

        // 기존 투표 카운트 감소
        if (oldVoteType == CommentVoteEntity.VoteType.UPVOTE) {
            comment.decreaseUpvoteCount();
        } else {
            comment.decreaseDownvoteCount();
        }

        // 새 투표 카운트 증가
        if (newVoteType == CommentVoteEntity.VoteType.UPVOTE) {
            comment.increaseUpvoteCount();
        } else {
            comment.increaseDownvoteCount();
        }

        // 투표 타입 변경
        vote.changeVote(newVoteType);
    }

    /**
     * Redis 캐시 업데이트 (게시글)
     */
    private void updatePostVoteCache(Long postId, CommunityPostEntity post) {
        try {
            String upvoteKey = POST_VOTE_KEY_PREFIX + postId + ":upvotes";
            String downvoteKey = POST_VOTE_KEY_PREFIX + postId + ":downvotes";
            String scoreKey = POST_VOTE_KEY_PREFIX + postId + ":score";

            redisTemplate.opsForValue().set(upvoteKey, String.valueOf(post.getUpvoteCount()));
            redisTemplate.opsForValue().set(downvoteKey, String.valueOf(post.getDownvoteCount()));
            redisTemplate.opsForValue().set(scoreKey, String.valueOf(post.getScore()));

            redisTemplate.expire(upvoteKey, VOTE_CACHE_TTL, TimeUnit.HOURS);
            redisTemplate.expire(downvoteKey, VOTE_CACHE_TTL, TimeUnit.HOURS);
            redisTemplate.expire(scoreKey, VOTE_CACHE_TTL, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to update vote cache for post: {}", postId, e);
        }
    }

    /**
     * Redis 캐시 업데이트 (댓글)
     */
    private void updateCommentVoteCache(Long commentId, CommunityCommentEntity comment) {
        try {
            String upvoteKey = COMMENT_VOTE_KEY_PREFIX + commentId + ":upvotes";
            String downvoteKey = COMMENT_VOTE_KEY_PREFIX + commentId + ":downvotes";
            String scoreKey = COMMENT_VOTE_KEY_PREFIX + commentId + ":score";

            redisTemplate.opsForValue().set(upvoteKey, String.valueOf(comment.getUpvoteCount()));
            redisTemplate.opsForValue().set(downvoteKey, String.valueOf(comment.getDownvoteCount()));
            redisTemplate.opsForValue().set(scoreKey, String.valueOf(comment.getScore()));

            redisTemplate.expire(upvoteKey, VOTE_CACHE_TTL, TimeUnit.HOURS);
            redisTemplate.expire(downvoteKey, VOTE_CACHE_TTL, TimeUnit.HOURS);
            redisTemplate.expire(scoreKey, VOTE_CACHE_TTL, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to update vote cache for comment: {}", commentId, e);
        }
    }

    /**
     * 게시글 투표 상태 조회
     */
    public PostVoteEntity.VoteType getPostVoteStatus(Long postId, Long userId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        return postVoteRepository.findByPostAndUser(post, user)
                .map(PostVoteEntity::getVoteType)
                .orElse(null);
    }

    /**
     * 댓글 투표 상태 조회
     */
    public CommentVoteEntity.VoteType getCommentVoteStatus(Long commentId, Long userId) {
        CommunityCommentEntity comment = commentRepository.findByCommentIdAndIsDeletedFalse(commentId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        return commentVoteRepository.findByCommentAndUser(comment, user)
                .map(CommentVoteEntity::getVoteType)
                .orElse(null);
    }
}
