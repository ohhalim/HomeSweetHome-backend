package com.homesweet.homesweetback.domain.community.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.homesweet.homesweetback.domain.community.entity.*;

import java.util.List;

/**
 * CommunityCommentLike 레포
 *
 * @author ohhalim777@gmail.com
 * @date 25. 10. 21.
 */
public interface CommunityCommentLikeRepository extends JpaRepository<CommunityCommentLikeEntity, Long> {
    boolean existsByComment_CommentIdAndUser_Id(Long commentId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CommunityCommentLikeEntity cl WHERE cl.comment.commentId = :commentId AND cl.user.id = :userId")
    int deleteByCommentIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO community_comment_likes (comment_id, user_id, created_at) VALUES (:commentId, :userId, NOW())", nativeQuery = true)
    int insertCommentLike(@Param("commentId") Long commentId, @Param("userId") Long userId);

    @Query("SELECT cl.user.id FROM CommunityCommentLikeEntity cl WHERE cl.comment.commentId = :commentId")
    List<Long> findUserIdsByCommentId(@Param("commentId") Long commentId);

    long countByComment_CommentId(Long commentId);
}