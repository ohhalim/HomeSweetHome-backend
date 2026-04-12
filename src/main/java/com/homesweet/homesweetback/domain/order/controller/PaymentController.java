package com.homesweet.homesweetback.domain.order.controller;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2UserPrincipal;
import com.homesweet.homesweetback.domain.order.dto.PaymentResponse;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;
import com.homesweet.homesweetback.domain.order.service.PaymentService;
import com.homesweet.homesweetback.domain.order.service.TossPaymentsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 토스페이먼츠 결제 API 컨트롤러
 *
 * 결제 흐름 (토스페이먼츠 문서 기준):
 * 1. POST /api/v1/orders - 주문 생성 (OrderController)
 * 2. 클라이언트에서 토스 결제위젯으로 결제 요청 (orderNumber, totalAmount 사용)
 * 3. 결제 완료 후 successUrl로 리다이렉트 (paymentKey, orderId, amount 전달)
 * 4. POST /api/v1/payments/confirm 호출하여 결제 승인
 */
@Tag(name = "Payment", description = "결제 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final TossPaymentsService tossPaymentsService;

    /**
     * 인증 정보에서 userId를 추출하는 헬퍼 메서드
     * testUserId 파라미터가 있으면 우선 사용 (k6 부하테스트용)
     */
    private Long getUserId(Long testUserId, Authentication authentication) {
        if (testUserId != null) {
            return testUserId;
        }
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() instanceof String) {
            log.warn("인증되지 않은 요청입니다. 테스트용 ID(1L)를 사용합니다.");
            return 1L;
        }
        try {
            OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
            return principal.getUserId();
        } catch (ClassCastException e) {
            log.error("Authentication Casting Error: {}", authentication.getPrincipal());
            return 1L;
        }
    }

    /**
     * 결제 승인 API
     * 토스 결제창 완료 후 successUrl에서 받은 paymentKey, orderId, amount로 결제 승인
     */
    @Operation(summary = "결제 승인", description = "토스페이먼츠 결제창 완료 후 최종 결제 승인")
    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @RequestParam(required = false) Long testUserId,
            @Valid @RequestBody TossPaymentConfirmRequest request,
            Authentication authentication) {

        Long userId = getUserId(testUserId, authentication);
        log.info("결제 승인 API 호출: userId={}, orderId={}, amount={}",
                userId, request.getOrderId(), request.getAmount());

        PaymentResponse response = paymentService.confirmPayment(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 결제 취소 API
     */
    @Operation(summary = "결제 취소", description = "결제 완료된 주문의 결제 취소")
    @PostMapping("/{paymentKey}/cancel")
     public ResponseEntity<PaymentResponse> cancelPayment(
            @RequestParam(required = false) Long testUserId,
            @PathVariable String paymentKey,
            @Valid @RequestBody TossPaymentCancelRequest request,
            Authentication authentication) {

        Long userId = getUserId(testUserId, authentication);
        log.info("결제 취소 API 호출: userId={}, paymentKey={}", userId, paymentKey);

        PaymentResponse response = paymentService.cancelPayment(userId, paymentKey, request);
        return ResponseEntity.ok(response);
    }

    /**
     * paymentKey로 결제 조회 API
     */
    @Operation(summary = "결제 조회", description = "paymentKey로 결제 정보 조회")
    @GetMapping("/{paymentKey}")
    public ResponseEntity<PaymentResponse> getPayment(
            @RequestParam(required = false) Long testUserId,
            @PathVariable String paymentKey,
            Authentication authentication) {

        Long userId = getUserId(testUserId, authentication);
        log.info("결제 조회 API 호출: userId={}, paymentKey={}", userId, paymentKey);

        PaymentResponse response = paymentService.getPayment(userId, paymentKey);
        return ResponseEntity.ok(response);
    }

    /**
     * orderId로 토스페이먼츠 결제 조회 API (외부 API 직접 조회)
     */
    @Operation(summary = "토스 결제 조회", description = "orderId로 토스페이먼츠 API 직접 조회")
    @GetMapping("/toss/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getTossPaymentByOrderId(
            @PathVariable String orderId) {

        log.info("토스 결제 조회 API 호출: orderId={}", orderId);

        Map<String, Object> response = tossPaymentsService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }
}
