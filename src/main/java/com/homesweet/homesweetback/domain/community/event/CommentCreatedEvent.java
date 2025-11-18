package com.homesweet.homesweetback.domain.community.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 댓글 생성 이벤트
 *
 * @author ohhalim777@gmail.com
 */
@Getter
public class CommentCreatedEvent extends ApplicationEvent {
    private final Long commentId;
    private final Long postId;
    private final Long userId;
    private final Long parentCommentId;

    public CommentCreatedEvent(Object source, Long commentId, Long postId, Long userId, Long parentCommentId) {
        super(source);
        this.commentId = commentId;
        this.postId = postId;
        this.userId = userId;
        this.parentCommentId = parentCommentId;
    }
}
