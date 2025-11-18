package com.homesweet.homesweetback.domain.community.dto;

import com.homesweet.homesweetback.domain.community.entity.PostVoteEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 투표 응답 DTO
 *
 * @author ohhalim777@gmail.com
 */
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "투표 응답")
public class VoteResponse {

    @Schema(description = "현재 투표 상태", example = "UPVOTE", allowableValues = {"UPVOTE", "DOWNVOTE", "NONE"})
    private PostVoteEntity.VoteType currentVote;

    @Schema(description = "총 Upvote 수", example = "150")
    private Integer upvoteCount;

    @Schema(description = "총 Downvote 수", example = "30")
    private Integer downvoteCount;

    @Schema(description = "점수 (upvotes - downvotes)", example = "120")
    private Integer score;

    public static VoteResponse of(PostVoteEntity.VoteType currentVote, Integer upvoteCount,
                                 Integer downvoteCount, Integer score) {
        return VoteResponse.builder()
                .currentVote(currentVote)
                .upvoteCount(upvoteCount)
                .downvoteCount(downvoteCount)
                .score(score)
                .build();
    }
}
