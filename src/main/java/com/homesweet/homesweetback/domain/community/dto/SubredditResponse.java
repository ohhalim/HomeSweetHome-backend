package com.homesweet.homesweetback.domain.community.dto;

import com.homesweet.homesweetback.domain.community.entity.SubredditEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 서브레딧 응답 DTO
 *
 * @author ohhalim777@gmail.com
 */
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "서브레딧 응답")
public class SubredditResponse {

    @Schema(description = "서브레딧 ID", example = "1")
    private Long subredditId;

    @Schema(description = "서브레딧 이름", example = "programming")
    private String name;

    @Schema(description = "서브레딧 제목", example = "프로그래밍")
    private String title;

    @Schema(description = "서브레딧 설명", example = "프로그래밍 토론 커뮤니티")
    private String description;

    @Schema(description = "생성자 ID", example = "1")
    private Long creatorId;

    @Schema(description = "생성자 이름", example = "홍길동")
    private String creatorName;

    @Schema(description = "구독자 수", example = "1500")
    private Integer subscriberCount;

    @Schema(description = "게시글 수", example = "350")
    private Integer postCount;

    @Schema(description = "활성화 여부", example = "true")
    private Boolean isActive;

    @Schema(description = "비공개 여부", example = "false")
    private Boolean isPrivate;

    @Schema(description = "성인 콘텐츠 여부", example = "false")
    private Boolean isNsfw;

    @Schema(description = "생성일시")
    private LocalDateTime createdAt;

    @Schema(description = "구독 여부 (현재 사용자)", example = "true")
    private Boolean isSubscribed;

    @Schema(description = "모더레이터 여부 (현재 사용자)", example = "false")
    private Boolean isModerator;

    /**
     * Entity → DTO 변환
     */
    public static SubredditResponse from(SubredditEntity entity) {
        return SubredditResponse.builder()
                .subredditId(entity.getSubredditId())
                .name(entity.getName())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .creatorId(entity.getCreator().getId())
                .creatorName(entity.getCreator().getName())
                .subscriberCount(entity.getSubscriberCount())
                .postCount(entity.getPostCount())
                .isActive(entity.getIsActive())
                .isPrivate(entity.getIsPrivate())
                .isNsfw(entity.getIsNsfw())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Entity → DTO 변환 (구독/모더레이터 정보 포함)
     */
    public static SubredditResponse from(SubredditEntity entity, boolean isSubscribed, boolean isModerator) {
        return SubredditResponse.builder()
                .subredditId(entity.getSubredditId())
                .name(entity.getName())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .creatorId(entity.getCreator().getId())
                .creatorName(entity.getCreator().getName())
                .subscriberCount(entity.getSubscriberCount())
                .postCount(entity.getPostCount())
                .isActive(entity.getIsActive())
                .isPrivate(entity.getIsPrivate())
                .isNsfw(entity.getIsNsfw())
                .createdAt(entity.getCreatedAt())
                .isSubscribed(isSubscribed)
                .isModerator(isModerator)
                .build();
    }
}
