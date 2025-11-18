package com.homesweet.homesweetback.domain.community.event;

import com.homesweet.homesweetback.domain.community.entity.PostVoteEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 게시글 투표 이벤트
 *
 * @author ohhalim777@gmail.com
 */
@Getter
public class PostVotedEvent extends ApplicationEvent {

    private final Long postId;
    private final Long userId;
    private final PostVoteEntity.VoteType voteType;
    private final Integer currentScore;
    private final boolean isUpvote;

    public PostVotedEvent(Object source, Long postId, Long userId,
                         PostVoteEntity.VoteType voteType, Integer currentScore) {
        super(source);
        this.postId = postId;
        this.userId = userId;
        this.voteType = voteType;
        this.currentScore = currentScore;
        this.isUpvote = voteType == PostVoteEntity.VoteType.UPVOTE;
    }
}
