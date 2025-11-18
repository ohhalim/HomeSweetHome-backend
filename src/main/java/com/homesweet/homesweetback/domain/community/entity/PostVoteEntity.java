package com.homesweet.homesweetback.domain.community.entity;

import com.homesweet.homesweetback.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 게시글 투표 엔티티 (Upvote/Downvote)
 *
 * Reddit 스타일 투표 시스템
 * - Upvote: +1 점수
 * - Downvote: -1 점수
 *
 * @author ohhalim777@gmail.com
 */
@Entity
@Table(name = "post_votes", indexes = {
    @Index(name = "idx_post_user", columnList = "post_id, user_id", unique = true),
    @Index(name = "idx_post", columnList = "post_id"),
    @Index(name = "idx_user", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@IdClass(PostVoteEntity.VoteId.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostVoteEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPostEntity post;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 투표 타입
     * UPVOTE: +1
     * DOWNVOTE: -1
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteType voteType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 투표 타입 열거형
     */
    public enum VoteType {
        UPVOTE(1),
        DOWNVOTE(-1);

        private final int value;

        VoteType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * 투표 변경
     */
    public void changeVote(VoteType newVoteType) {
        this.voteType = newVoteType;
    }

    /**
     * 복합 키 클래스
     */
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoteId implements Serializable {
        private Long post;
        private Long user;
    }
}
