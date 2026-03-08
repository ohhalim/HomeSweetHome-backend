package com.homesweet.homesweetback.domain.order.controller;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2UserPrincipal;
import com.homesweet.homesweetback.domain.order.dto.CreateOrderRequest;
import com.homesweet.homesweetback.domain.order.dto.OrderResponse;
import com.homesweet.homesweetback.domain.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 API 컨트롤러
 *
 * 주문 흐름:
 * 1. POST /api/v1/orders - 장바구니에서 주문 생성
 * 2. 클라이언트에서 토스페이먼츠 결제창 호출 (orderNumber, totalAmount 사용)
 * 3. 결제 완료 후 POST /api/v1/payments/confirm 호출
 */
@Tag(name = "Order", description = "주문 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
 
    private final OrderService orderService;

    /**
     * 주문 생성 API
     * 장바구니에서 선택한 상품들로 주문을 생성합니다.
     * 생성된 주문의 orderNumber, totalAmount로 토스페이먼츠 결제창을 호출하세요.
     */
    @Operation(summary = "주문 생성", description = "장바구니에서 주문 생성. 결제 전 단계.")
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request) {

        log.info("주문 생성 API 호출: userId={}", principal.getUserId());
        OrderResponse response = orderService.createFromCart(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 내 주문 목록 조회 API
     */
    @Operation(summary = "내 주문 목록", description = "로그인한 사용자의 주문 목록 조회")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal OAuth2UserPrincipal principal) {

        log.info("주문 목록 조회 API 호출: userId={}", principal.getUserId());
        List<OrderResponse> orders = orderService.getMyOrders(principal.getUserId());
        return ResponseEntity.ok(orders);
    }

    /**
     * 주문 상세 조회 API
     */
    @Operation(summary = "주문 상세 조회", description = "주문 ID로 상세 정보 조회")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @PathVariable Long orderId) {

        log.info("주문 상세 조회 API 호출: userId={}, orderId={}", principal.getUserId(), orderId);
        OrderResponse response = orderService.getOrder(orderId, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * 주문 취소 API
     * 결제 전 PENDING 상태에서만 취소 가능
     */
    @Operation(summary = "주문 취소", description = "결제 전 주문 취소 (PENDING 상태에서만 가능)")
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @PathVariable Long orderId) {

        log.info("주문 취소 API 호출: userId={}, orderId={}", principal.getUserId(), orderId);
        orderService.cancelOrder(orderId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
