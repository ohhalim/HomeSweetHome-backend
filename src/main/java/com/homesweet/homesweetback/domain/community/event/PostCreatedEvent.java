package com.homesweet.homesweetback.domain.community.event;

import lombok.Getter;

/**
 * 게시글 생성 이벤트
 *
 * @author ohhalim777@gmail.com
 */
@Getter
public class PostCreatedEvent extends CommunityPostEvent {
    private final String title;
    private final String category;

    public PostCreatedEvent(Object source, Long postId, Long userId, String title, String category) {
        super(source, postId, userId, "POST_CREATED");
        this.title = title;
        this.category = category;
    }
}
