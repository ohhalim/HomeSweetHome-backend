package com.homesweet.homesweetback.domain.community.repository;

import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * CommunityPost 레포
 *
 * @author ohhalim777@gmail.com
 * @date 25. 10. 21.
 */

public interface CommunityPostRepository extends JpaRepository<CommunityPostEntity, Long> {

    // 특정 게시글 조회 (N+1 방지: author Fetch Join)
    @EntityGraph(attributePaths = {"author"})
    Optional<CommunityPostEntity> findByPostIdAndIsDeletedFalse(Long postId);

    // 비관적 락을 사용한 게시글 조회 (동시성 제어용)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CommunityPostEntity p JOIN FETCH p.author WHERE p.postId = :postId AND p.isDeleted = false")
    Optional<CommunityPostEntity> findByPostIdAndIsDeletedFalseWithPessimisticLock(@Param("postId") Long postId);

    // 페이지네이션 쿼리 메서드 (N+1 방지: author Fetch Join)
    @EntityGraph(attributePaths = {"author"})
    Page<CommunityPostEntity> findByIsDeletedFalse(Pageable pageable);

    // 카테고리별 페이지네이션 (N+1 방지)
    @EntityGraph(attributePaths = {"author"})
    Page<CommunityPostEntity> findByCategoryAndIsDeletedFalse(String category, Pageable pageable);

    // 인기 게시글 조회 (좋아요 수 기준, N+1 방지)
    @Query("SELECT p FROM CommunityPostEntity p JOIN FETCH p.author WHERE p.isDeleted = false ORDER BY p.likeCount DESC, p.createdAt DESC")
    Page<CommunityPostEntity> findPopularPosts(Pageable pageable);

    // 트렌딩 게시글 조회 (조회수 + 좋아요 조합, N+1 방지)
    @Query("SELECT p FROM CommunityPostEntity p JOIN FETCH p.author WHERE p.isDeleted = false ORDER BY (p.viewCount + p.likeCount * 2) DESC, p.createdAt DESC")
    Page<CommunityPostEntity> findTrendingPosts(Pageable pageable);
}