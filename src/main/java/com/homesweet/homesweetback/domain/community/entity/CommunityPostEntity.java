package com.homesweet.homesweetback.domain.community.entity;

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

@Entity
@Table(name = "community_posts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityPostEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false, length = 10)
    private String category;

    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer commentCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isModified = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * 게시글 내용 수정
     */
    public void updatePost(String title, String content, String category) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.isModified = true;
        this.modifiedAt = LocalDateTime.now();
    }

    /**
     * 작성자 본인 확인
     */
    public boolean isAuthor(Long userId) {
        return this.author.getId().equals(userId);
    }

    /**
     * 게시글 소프트 삭제
     */
    public void deletePost() {
        this.isDeleted = true;
    }

    /**
     * 게시글 조회수 카운트
     */
    public void increaseViewCount() { this.viewCount++; }

    /**
     * 게시글 좋아요 카운트
     */
    public void increaseLikeCount() { this.likeCount++; }
    public void decreaseLikeCount() { this.likeCount--; }

    /**
     * 게시글 댓글 카운트
     */
    public void increaseCommentCount() { this.commentCount++; }
    public void decreaseCommentCount() { this.commentCount--; }
}
