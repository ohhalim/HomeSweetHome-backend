package com.homesweet.homesweetback.domain.community.repository;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.entity.PostVoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Post Vote 레포지토리 (Upvote/Downvote)
 *
 * @author ohhalim777@gmail.com
 */
public interface PostVoteRepository extends JpaRepository<PostVoteEntity, PostVoteEntity.VoteId> {

    /**
     * 투표 조회
     */
    Optional<PostVoteEntity> findByPostAndUser(CommunityPostEntity post, User user);

    /**
     * 투표 여부 확인
     */
    boolean existsByPostAndUser(CommunityPostEntity post, User user);

    /**
     * 게시글의 Upvote 수 조회
     */
    @Query("SELECT COUNT(v) FROM PostVoteEntity v WHERE v.post = :post AND v.voteType = 'UPVOTE'")
    long countUpvotesByPost(@Param("post") CommunityPostEntity post);

    /**
     * 게시글의 Downvote 수 조회
     */
    @Query("SELECT COUNT(v) FROM PostVoteEntity v WHERE v.post = :post AND v.voteType = 'DOWNVOTE'")
    long countDownvotesByPost(@Param("post") CommunityPostEntity post);

    /**
     * 사용자가 받은 총 Upvote 수 (카르마 계산용)
     */
    @Query("SELECT COUNT(v) FROM PostVoteEntity v WHERE v.post.author = :user AND v.voteType = 'UPVOTE'")
    long countUpvotesByAuthor(@Param("user") User user);

    /**
     * 사용자가 받은 총 Downvote 수 (카르마 계산용)
     */
    @Query("SELECT COUNT(v) FROM PostVoteEntity v WHERE v.post.author = :user AND v.voteType = 'DOWNVOTE'")
    long countDownvotesByAuthor(@Param("user") User user);
}
