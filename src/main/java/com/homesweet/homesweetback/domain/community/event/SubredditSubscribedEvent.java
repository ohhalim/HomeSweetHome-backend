package com.homesweet.homesweetback.domain.community.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 서브레딧 구독 이벤트
 *
 * @author ohhalim777@gmail.com
 */
@Getter
public class SubredditSubscribedEvent extends ApplicationEvent {

    private final Long subredditId;
    private final Long userId;
    private final boolean isSubscribe; // true: 구독, false: 구독 취소

    public SubredditSubscribedEvent(Object source, Long subredditId, Long userId, boolean isSubscribe) {
        super(source);
        this.subredditId = subredditId;
        this.userId = userId;
        this.isSubscribe = isSubscribe;
    }
}
