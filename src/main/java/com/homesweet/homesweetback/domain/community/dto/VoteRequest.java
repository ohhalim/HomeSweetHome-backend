package com.homesweet.homesweetback.domain.community.dto;

import com.homesweet.homesweetback.domain.community.entity.PostVoteEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 투표 요청 DTO
 *
 * @author ohhalim777@gmail.com
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "투표 요청")
public class VoteRequest {

    @NotNull(message = "투표 타입은 필수입니다")
    @Schema(description = "투표 타입", example = "UPVOTE", allowableValues = {"UPVOTE", "DOWNVOTE"}, required = true)
    private PostVoteEntity.VoteType voteType;
}
