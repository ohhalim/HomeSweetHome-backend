package com.homesweet.homesweetback.domain.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서브레딧 생성 요청 DTO
 *
 * @author ohhalim777@gmail.com
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "서브레딧 생성 요청")
public class SubredditCreateRequest {

    @NotBlank(message = "서브레딧 이름은 필수입니다")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,21}$",
            message = "서브레딧 이름은 영문, 숫자, 언더스코어만 사용 가능하며 3-21자여야 합니다")
    @Schema(description = "서브레딧 이름 (URL-friendly)", example = "programming", required = true)
    private String name;

    @NotBlank(message = "서브레딧 제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자 이내여야 합니다")
    @Schema(description = "서브레딧 제목", example = "프로그래밍", required = true)
    private String title;

    @Size(max = 500, message = "설명은 500자 이내여야 합니다")
    @Schema(description = "서브레딧 설명", example = "프로그래밍 토론 커뮤니티")
    private String description;

    @Schema(description = "비공개 여부", example = "false")
    private Boolean isPrivate;

    @Schema(description = "성인 콘텐츠 여부", example = "false")
    private Boolean isNsfw;
}
