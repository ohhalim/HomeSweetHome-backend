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
 * 댓글 투표 엔티티 (Upvote/Downvote)
 *
 * @author ohhalim777@gmail.com
 */
@Entity
@Table(name = "comment_votes", indexes = {
    @Index(name = "idx_comment_user", columnList = "comment_id, user_id", unique = true),
    @Index(name = "idx_comment", columnList = "comment_id"),
    @Index(name = "idx_user", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@IdClass(CommentVoteEntity.VoteId.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentVoteEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private CommunityCommentEntity comment;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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
     * 투표 타입
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
        private Long comment;
        private Long user;
    }
}
