package com.homesweet.homesweetback.domain.community.repository;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.community.entity.SubredditEntity;
import com.homesweet.homesweetback.domain.community.entity.SubredditModeratorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Subreddit Moderator 레포지토리
 *
 * @author ohhalim777@gmail.com
 */
public interface SubredditModeratorRepository extends JpaRepository<SubredditModeratorEntity, Long> {

    /**
     * 서브레딧의 모더레이터 목록 조회
     */
    List<SubredditModeratorEntity> findBySubredditOrderByAppointedAtAsc(SubredditEntity subreddit);

    /**
     * 모더레이터 여부 확인
     */
    boolean existsBySubredditAndUser(SubredditEntity subreddit, User user);

    /**
     * 모더레이터 조회
     */
    Optional<SubredditModeratorEntity> findBySubredditAndUser(SubredditEntity subreddit, User user);

    /**
     * 사용자가 관리하는 서브레딧 목록 조회
     */
    @Query("SELECT m FROM SubredditModeratorEntity m WHERE m.user = :user ORDER BY m.appointedAt DESC")
    List<SubredditModeratorEntity> findByUserOrderByAppointedAtDesc(User user);

    /**
     * 서브레딧의 FULL 권한 모더레이터 수
     */
    long countBySubredditAndPermission(SubredditEntity subreddit, SubredditModeratorEntity.ModeratorPermission permission);
}
