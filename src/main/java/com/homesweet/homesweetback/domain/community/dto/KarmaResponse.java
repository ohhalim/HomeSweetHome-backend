package com.homesweet.homesweetback.domain.community.dto;

import com.homesweet.homesweetback.domain.community.service.KarmaService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 카르마 응답 DTO
 *
 * @author ohhalim777@gmail.com
 */
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "카르마 응답")
public class KarmaResponse {

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "Post 카르마", example = "1500")
    private Long postKarma;

    @Schema(description = "Post Upvote 수", example = "1800")
    private Long postUpvotes;

    @Schema(description = "Post Downvote 수", example = "300")
    private Long postDownvotes;

    @Schema(description = "Comment 카르마", example = "800")
    private Long commentKarma;

    @Schema(description = "Comment Upvote 수", example = "950")
    private Long commentUpvotes;

    @Schema(description = "Comment Downvote 수", example = "150")
    private Long commentDownvotes;

    @Schema(description = "총 카르마", example = "2300")
    private Long totalKarma;

    @Schema(description = "카르마 레벨", example = "CONTRIBUTOR")
    private KarmaService.KarmaLevel level;

    @Schema(description = "레벨 표시명", example = "기여자")
    private String levelDisplayName;

    public static KarmaResponse from(KarmaService.KarmaDetail detail, KarmaService.KarmaLevel level) {
        return KarmaResponse.builder()
                .userId(detail.getUserId())
                .postKarma(detail.getPostKarma())
                .postUpvotes(detail.getPostUpvotes())
                .postDownvotes(detail.getPostDownvotes())
                .commentKarma(detail.getCommentKarma())
                .commentUpvotes(detail.getCommentUpvotes())
                .commentDownvotes(detail.getCommentDownvotes())
                .totalKarma(detail.getTotalKarma())
                .level(level)
                .levelDisplayName(level.getDisplayName())
                .build();
    }
}
