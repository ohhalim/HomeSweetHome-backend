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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
     * 결제 승인 API
     * 토스 결제창 완료 후 successUrl에서 받은 paymentKey, orderId, amount로 결제 승인
     */
    @Operation(summary = "결제 승인", description = "토스페이먼츠 결제창 완료 후 최종 결제 승인")
    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @Valid @RequestBody TossPaymentConfirmRequest request) {

        log.info("결제 승인 API 호출: userId={}, orderId={}, amount={}",
                principal.getUserId(), request.getOrderId(), request.getAmount());

        PaymentResponse response = paymentService.confirmPayment(principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * 결제 취소 API
     */
    @Operation(summary = "결제 취소", description = "결제 완료된 주문의 결제 취소")
    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @PathVariable String paymentKey,
            @RequestBody TossPaymentCancelRequest request) {

        log.info("결제 취소 API 호출: userId={}, paymentKey={}", principal.getUserId(), paymentKey);

        PaymentResponse response = paymentService.cancelPayment(principal.getUserId(), paymentKey, request);
        return ResponseEntity.ok(response);
    }

    /**
     * paymentKey로 결제 조회 API
     */
    @Operation(summary = "결제 조회", description = "paymentKey로 결제 정보 조회")
    @GetMapping("/{paymentKey}")
    public ResponseEntity<PaymentResponse> getPayment(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @PathVariable String paymentKey) {

        log.info("결제 조회 API 호출: userId={}, paymentKey={}", principal.getUserId(), paymentKey);

        PaymentResponse response = paymentService.getPayment(principal.getUserId(), paymentKey);
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
