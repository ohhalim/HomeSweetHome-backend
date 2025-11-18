package com.homesweet.homesweetback.domain.community.repository;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.community.entity.CommentVoteEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Comment Vote 레포지토리 (Upvote/Downvote)
 *
 * @author ohhalim777@gmail.com
 */
public interface CommentVoteRepository extends JpaRepository<CommentVoteEntity, CommentVoteEntity.VoteId> {

    /**
     * 투표 조회
     */
    Optional<CommentVoteEntity> findByCommentAndUser(CommunityCommentEntity comment, User user);

    /**
     * 투표 여부 확인
     */
    boolean existsByCommentAndUser(CommunityCommentEntity comment, User user);

    /**
     * 댓글의 Upvote 수 조회
     */
    @Query("SELECT COUNT(v) FROM CommentVoteEntity v WHERE v.comment = :comment AND v.voteType = 'UPVOTE'")
    long countUpvotesByComment(@Param("comment") CommunityCommentEntity comment);

    /**
     * 댓글의 Downvote 수 조회
     */
    @Query("SELECT COUNT(v) FROM CommentVoteEntity v WHERE v.comment = :comment AND v.voteType = 'DOWNVOTE'")
    long countDownvotesByComment(@Param("comment") CommunityCommentEntity comment);

    /**
     * 사용자가 받은 총 Upvote 수 (카르마 계산용)
     */
    @Query("SELECT COUNT(v) FROM CommentVoteEntity v WHERE v.comment.author = :user AND v.voteType = 'UPVOTE'")
    long countUpvotesByAuthor(@Param("user") User user);

    /**
     * 사용자가 받은 총 Downvote 수 (카르마 계산용)
     */
    @Query("SELECT COUNT(v) FROM CommentVoteEntity v WHERE v.comment.author = :user AND v.voteType = 'DOWNVOTE'")
    long countDownvotesByAuthor(@Param("user") User user);
}
