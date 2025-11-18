package com.homesweet.homesweetback.domain.subreddit.entity;

import com.homesweet.homesweetback.common.BaseEntity;
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
 * Subreddit 엔티티 (Reddit 스타일 서브 커뮤니티)
 *
 * r/programming, r/movies 같은 주제별 독립 커뮤니티
 *
 * @author ohhalim777@gmail.com
 */
@Entity
@Table(name = "subreddits", indexes = {
    @Index(name = "idx_name", columnList = "name", unique = true),
    @Index(name = "idx_subscriber_count", columnList = "subscriber_count DESC"),
    @Index(name = "idx_created_at", columnList = "created_at DESC"),
    @Index(name = "idx_is_active", columnList = "is_active")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubredditEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subreddit_id")
    private Long subredditId;

    /**
     * 서브레딧 이름 (예: programming, movies)
     * URL에서 r/{name} 형태로 사용
     */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * 표시 제목 (예: Programming, Movies & TV Shows)
     */
    @Column(nullable = false, length = 100)
    private String title;

    /**
     * 설명
     */
    @Column(length = 500)
    private String description;

    /**
     * 규칙 (JSON 또는 텍스트)
     */
    @Column(length = 2000)
    private String rules;

    /**
     * 생성자 (서브레딧 창설자)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    /**
     * 구독자 수
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer subscriberCount = 0;

    /**
     * 게시글 수
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer postCount = 0;

    /**
     * 활성 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 비공개 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPrivate = false;

    /**
     * 18+ 제한
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isNsfw = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 구독자 수 증가
     */
    public void increaseSubscriberCount() {
        this.subscriberCount++;
    }

    /**
     * 구독자 수 감소
     */
    public void decreaseSubscriberCount() {
        if (this.subscriberCount > 0) {
            this.subscriberCount--;
        }
    }

    /**
     * 게시글 수 증가
     */
    public void increasePostCount() {
        this.postCount++;
    }

    /**
     * 게시글 수 감소
     */
    public void decreasePostCount() {
        if (this.postCount > 0) {
            this.postCount--;
        }
    }

    /**
     * 서브레딧 업데이트
     */
    public void updateSubreddit(String title, String description, String rules) {
        this.title = title;
        this.description = description;
        this.rules = rules;
    }

    /**
     * 서브레딧 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 생성자 확인
     */
    public boolean isCreator(Long userId) {
        return this.creator.getId().equals(userId);
    }
}
