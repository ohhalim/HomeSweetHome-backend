package com.homesweet.homesweetback.domain.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구독 생성 요청 DTO
 * 토스 빌링키 발급 후 successUrl에서 받은 정보
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionRequest {

    /**
     * 토스 빌링 인증 키 (successUrl에서 전달받음)
     */
    @NotBlank(message = "authKey는 필수입니다.")
    private String authKey;

    /**
     * 고객 고유 키 (프론트에서 생성해서 전달)
     */
    @NotBlank(message = "customerKey는 필수입니다.")
    private String customerKey;
}
