package com.homesweet.homesweetback.domain.community.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.homesweet.homesweetback.domain.community.entity.*;

import java.util.List;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLikeEntity, Long> {
    boolean existsByPost_PostIdAndUser_Id(Long postId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CommunityPostLikeEntity pl WHERE pl.post.postId = :postId AND pl.user.id = :userId")
    int deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO community_post_likes (post_id, user_id, created_at) VALUES (:postId, :userId, NOW())", nativeQuery = true)
    int insertPostLike(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query("SELECT pl.user.id FROM CommunityPostLikeEntity pl WHERE pl.post.postId = :postId")
    List<Long> findUserIdsByPostId(@Param("postId") Long postId);

    long countByPost_PostId(Long postId);
}
