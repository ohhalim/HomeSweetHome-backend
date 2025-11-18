package com.homesweet.homesweetback.domain.subreddit.entity;

import com.homesweet.homesweetback.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 서브레딧 운영자 엔티티
 *
 * @author ohhalim777@gmail.com
 */
@Entity
@Table(name = "subreddit_moderators", indexes = {
    @Index(name = "idx_subreddit", columnList = "subreddit_id"),
    @Index(name = "idx_user_subreddit", columnList = "user_id, subreddit_id", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubredditModeratorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "moderator_id")
    private Long moderatorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subreddit_id", nullable = false)
    private SubredditEntity subreddit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 권한 레벨
     * - FULL: 모든 권한 (운영자 추가/제거 포함)
     * - POSTS: 게시글 관리
     * - COMMENTS: 댓글 관리
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModeratorPermission permission;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime appointedAt;

    public enum ModeratorPermission {
        FULL,      // 모든 권한
        POSTS,     // 게시글 관리만
        COMMENTS   // 댓글 관리만
    }
}
