package com.homesweet.homesweetback.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토스페이먼츠 결제 취소 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TossPaymentCancelRequest {

    @NotBlank(message = "취소 사유는 필수입니다.")
    private String cancelReason;

    @Positive(message = "부분 취소 금액은 1 이상이어야 합니다.")
    private Long cancelAmount;
}
