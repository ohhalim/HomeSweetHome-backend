package com.homesweet.homesweetback.domain.community.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 커뮤니티 게시글 이벤트 베이스 클래스
 *
 * @author ohhalim777@gmail.com
 */
@Getter
public abstract class CommunityPostEvent extends ApplicationEvent {
    private final Long postId;
    private final Long userId;
    private final String eventType;

    protected CommunityPostEvent(Object source, Long postId, Long userId, String eventType) {
        super(source);
        this.postId = postId;
        this.userId = userId;
        this.eventType = eventType;
    }
}
