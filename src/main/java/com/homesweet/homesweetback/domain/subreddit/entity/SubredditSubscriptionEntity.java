package com.homesweet.homesweetback.domain.subreddit.entity;

import com.homesweet.homesweetback.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 서브레딧 구독 엔티티
 *
 * @author ohhalim777@gmail.com
 */
@Entity
@Table(name = "subreddit_subscriptions", indexes = {
    @Index(name = "idx_user_subreddit", columnList = "user_id, subreddit_id", unique = true),
    @Index(name = "idx_subreddit", columnList = "subreddit_id"),
    @Index(name = "idx_user", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@IdClass(SubredditSubscriptionEntity.SubscriptionId.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubredditSubscriptionEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subreddit_id", nullable = false)
    private SubredditEntity subreddit;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime subscribedAt;

    /**
     * 복합 키 클래스
     */
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionId implements Serializable {
        private Long user;
        private Long subreddit;
    }
}
