package com.homesweet.homesweetback.domain.community.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.homesweet.homesweetback.domain.community.entity.*;
import java.util.List;

/**
 * CommunityComment 레포
 *
 * @author ohhalim777@gmail.com
 * @date 25. 10. 21.
 */

public interface CommunityCommentRepository extends JpaRepository<CommunityCommentEntity, Long> {

    // 특정 게시글의 댓글 조회 - N+1 문제 해결
    @EntityGraph(attributePaths = {"author"})
    List<CommunityCommentEntity> findByPost_PostIdAndIsDeletedFalse(Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CommunityCommentEntity c SET c.likeCount = c.likeCount + :delta WHERE c.commentId = :commentId")
    int updateLikeCount(@Param("commentId") Long commentId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE CommunityCommentEntity c SET c.likeCount = :likeCount WHERE c.commentId = :commentId AND c.isDeleted = false")
    int setLikeCount(@Param("commentId") Long commentId, @Param("likeCount") Integer likeCount);

}
