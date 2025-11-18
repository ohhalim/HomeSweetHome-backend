package com.homesweet.homesweetback.domain.community.repository;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.community.entity.SubredditEntity;
import com.homesweet.homesweetback.domain.community.entity.SubredditSubscriptionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Subreddit 구독 레포지토리
 *
 * @author ohhalim777@gmail.com
 */
public interface SubredditSubscriptionRepository extends JpaRepository<SubredditSubscriptionEntity, SubredditSubscriptionEntity.SubscriptionId> {

    /**
     * 구독 여부 확인
     */
    boolean existsBySubredditAndUser(SubredditEntity subreddit, User user);

    /**
     * 구독 조회
     */
    Optional<SubredditSubscriptionEntity> findBySubredditAndUser(SubredditEntity subreddit, User user);

    /**
     * 사용자의 구독 목록 조회
     */
    @Query("SELECT s FROM SubredditSubscriptionEntity s WHERE s.user = :user ORDER BY s.subscribedAt DESC")
    Page<SubredditSubscriptionEntity> findByUserOrderBySubscribedAtDesc(User user, Pageable pageable);

    /**
     * 서브레딧의 구독자 수 조회
     */
    long countBySubreddit(SubredditEntity subreddit);
}
