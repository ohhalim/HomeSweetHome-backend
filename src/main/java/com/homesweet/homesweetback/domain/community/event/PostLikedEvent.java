package com.homesweet.homesweetback.domain.community.event;

import lombok.Getter;

/**
 * 게시글 좋아요 이벤트
 *
 * @author ohhalim777@gmail.com
 */
@Getter
public class PostLikedEvent extends CommunityPostEvent {
    private final boolean isLiked;  // true: 좋아요, false: 좋아요 취소

    public PostLikedEvent(Object source, Long postId, Long userId, boolean isLiked) {
        super(source, postId, userId, "POST_LIKED");
        this.isLiked = isLiked;
    }
}
