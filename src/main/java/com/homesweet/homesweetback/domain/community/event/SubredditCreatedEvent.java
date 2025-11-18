package com.homesweet.homesweetback.domain.community.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 서브레딧 생성 이벤트
 *
 * @author ohhalim777@gmail.com
 */
@Getter
public class SubredditCreatedEvent extends ApplicationEvent {

    private final Long subredditId;
    private final String name;
    private final Long creatorId;

    public SubredditCreatedEvent(Object source, Long subredditId, String name, Long creatorId) {
        super(source);
        this.subredditId = subredditId;
        this.name = name;
        this.creatorId = creatorId;
    }
}
