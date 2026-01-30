package com.homesweet.homesweetback.domain.subscription.controller;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2UserPrincipal;
import com.homesweet.homesweetback.domain.subscription.dto.CreateSubscriptionRequest;
import com.homesweet.homesweetback.domain.subscription.dto.SubscriptionResponse;
import com.homesweet.homesweetback.domain.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 구독 API 컨트롤러
 *
 * 구독 흐름:
 * 1. 프론트엔드에서 토스 SDK requestBillingAuth() 호출
 * 2. 사용자 카드 등록 완료 후 successUrl로 리다이렉트 (authKey 전달)
 * 3. POST /api/v1/subscriptions 호출하여 구독 시작
 * 4. 첫 달은 무료, 30일 후부터 자동 결제
 */
@Tag(name = "Subscription", description = "프리미엄 구독 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * 구독 생성 (첫 달 무료)
     * 토스 카드 등록 완료 후 호출
     */
    @Operation(summary = "구독 시작", description = "프리미엄 구독 시작 (첫 달 무료)")
    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @Valid @RequestBody CreateSubscriptionRequest request) {

        log.info("구독 생성 API 호출: userId={}", principal.getUserId());
        SubscriptionResponse response = subscriptionService.createSubscription(
                principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * 내 구독 정보 조회
     */
    @Operation(summary = "내 구독 조회", description = "현재 구독 상태 및 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponse> getMySubscription(
            @AuthenticationPrincipal OAuth2UserPrincipal principal) {

        log.info("구독 조회 API 호출: userId={}", principal.getUserId());
        SubscriptionResponse response = subscriptionService.getMySubscription(principal.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * 구독 취소
     * 다음 결제부터 자동결제 중지, 현재 기간은 계속 이용 가능
     */
    @Operation(summary = "구독 취소", description = "다음 결제부터 구독 취소 (현재 기간은 유지)")
    @DeleteMapping
    public ResponseEntity<Void> cancelSubscription(
            @AuthenticationPrincipal OAuth2UserPrincipal principal) {

        log.info("구독 취소 API 호출: userId={}", principal.getUserId());
        subscriptionService.cancelSubscription(principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 구독 상태 확인 API
     * 프론트엔드에서 사용자의 구독 여부를 빠르게 확인할 때 사용
     */
    @Operation(summary = "구독 상태 확인", description = "현재 사용자가 구독 중인지 확인")
    @GetMapping("/status")
    public ResponseEntity<Boolean> checkSubscriptionStatus(
            @AuthenticationPrincipal OAuth2UserPrincipal principal) {

        boolean isActive = subscriptionService.isActiveSubscriber(principal.getUserId());
        return ResponseEntity.ok(isActive);
    }
}
