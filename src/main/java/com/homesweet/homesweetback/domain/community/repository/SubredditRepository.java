package com.homesweet.homesweetback.domain.community.repository;

import com.homesweet.homesweetback.domain.community.entity.SubredditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Subreddit 레포지토리
 *
 * @author ohhalim777@gmail.com
 */
public interface SubredditRepository extends JpaRepository<SubredditEntity, Long> {

    /**
     * 서브레딧 이름으로 조회
     */
    Optional<SubredditEntity> findByNameAndIsActiveTrueAndIsDeletedFalse(String name);

    /**
     * 서브레딧 존재 여부 확인
     */
    boolean existsByName(String name);

    /**
     * 활성 서브레딧 목록 조회 (구독자 수 내림차순)
     */
    @Query("SELECT s FROM SubredditEntity s WHERE s.isActive = true AND s.isDeleted = false ORDER BY s.subscriberCount DESC")
    Page<SubredditEntity> findActiveSubredditsBySubscriberCount(Pageable pageable);

    /**
     * 활성 서브레딧 목록 조회 (게시글 수 내림차순)
     */
    @Query("SELECT s FROM SubredditEntity s WHERE s.isActive = true AND s.isDeleted = false ORDER BY s.postCount DESC")
    Page<SubredditEntity> findActiveSubredditsByPostCount(Pageable pageable);

    /**
     * 서브레딧 검색
     */
    @Query("SELECT s FROM SubredditEntity s WHERE s.isActive = true AND s.isDeleted = false " +
           "AND (s.name LIKE %:keyword% OR s.title LIKE %:keyword% OR s.description LIKE %:keyword%)")
    Page<SubredditEntity> searchSubreddits(@Param("keyword") String keyword, Pageable pageable);

    /**
     * ID로 활성 서브레딧 조회
     */
    Optional<SubredditEntity> findBySubredditIdAndIsActiveTrueAndIsDeletedFalse(Long subredditId);
}
