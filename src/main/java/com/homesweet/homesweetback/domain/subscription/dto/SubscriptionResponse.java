package com.homesweet.homesweetback.domain.subscription.dto;

import com.homesweet.homesweetback.domain.subscription.entity.Subscription;
import com.homesweet.homesweetback.domain.subscription.entity.SubscriptionPlan;
import com.homesweet.homesweetback.domain.subscription.entity.SubscriptionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 구독 응답 DTO
 */
@Getter
@Builder
public class SubscriptionResponse {

    private Long subscriptionId;
    private Long userId;
    private SubscriptionPlan plan;
    private String planDisplayName;
    private Long pricePerMonth;
    private SubscriptionStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextPaymentDate;
    private LocalDateTime createdAt;

    public static SubscriptionResponse from(Subscription subscription) {
        return SubscriptionResponse.builder()
                .subscriptionId(subscription.getId())
                .userId(subscription.getUser().getId())
                .plan(subscription.getPlan())
                .planDisplayName(subscription.getPlan().getDisplayName())
                .pricePerMonth(subscription.getPlan().getPrice())
                .status(subscription.getStatus())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .nextPaymentDate(subscription.getNextPaymentDate())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
