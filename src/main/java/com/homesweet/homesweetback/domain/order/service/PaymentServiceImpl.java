package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.common.exception.OrderNotFoundException;
import com.homesweet.homesweetback.common.exception.PaymentMismatchException;
import com.homesweet.homesweetback.domain.order.dto.PaymentResponse;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.Payment;
import com.homesweet.homesweetback.domain.order.entity.PaymentStatus;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.order.repository.PaymentRepository;
import com.homesweet.homesweetback.domain.product.cart.repository.jpa.CartJPARepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 결제 서비스 구현 (최소 구현)
 *
 * 흐름:
 * 1. 토스 승인 API 호출
 * 2. DB에 결제 정보 저장 + 주문 상태 변경
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final TossPaymentsService tossPaymentsService;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CartJPARepository cartJPARepository;

    /**
     * 결제 승인 처리
     * 1. 이미 결제된 건인지 확인 (중복방지)
     * 2. 토스 API에 결제 승인 요청
     * 3. DB에 저장 (결제 + 주문 상태 변경 + 장바구니 삭제)
     */
    @Override
    @Transactional
    public PaymentResponse confirmPayment(Long userId, TossPaymentConfirmRequest request) {
        // 1. 이미 결제 완료된 건인지 확인 (멱등성 보장)
        Optional<Payment> existingPayment = paymentRepository.findByPaymentKey(request.getPaymentKey());
        if (existingPayment.isPresent()) {
            log.info("이미 처리된 결제: paymentKey={}", request.getPaymentKey());
            return PaymentResponse.from(existingPayment.get());
        }

        // 2. 주문 조회 및 검증
        Order order = orderRepository.findByOrderNumberWithItemsForUpdate(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(
                        "주문을 찾을 수 없습니다. orderId=" + request.getOrderId()));

        if (!order.isOwner(userId)) {
            throw new IllegalArgumentException("본인의 주문만 결제할 수 있습니다.");
        }

        if (!order.isPending()) {
            throw new IllegalStateException("결제 가능한 상태가 아닙니다.");
        }

        if (!order.getTotalAmount().equals(request.getAmount())) {
            throw new PaymentMismatchException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }

        // 3. 토스 결제 승인 API 호출
        Map<String, Object> tossResponse = tossPaymentsService.confirmPayment(request);

        // 4. 결제 정보 DB 저장
        Payment payment = Payment.builder()
                .order(order)
                .paymentKey(request.getPaymentKey())
                .tossOrderId(request.getOrderId())
                .status(PaymentStatus.READY)
                .amount(request.getAmount())
                .requestedAt(parseDateTime(tossResponse.get("requestedAt")))
                .build();

        String method = extractString(tossResponse, "method");
        LocalDateTime approvedAt = parseDateTime(tossResponse.get("approvedAt"));
        String receiptUrl = extractReceiptUrl(tossResponse);

        payment.complete(method, approvedAt, receiptUrl);
        paymentRepository.save(payment);

        // 5. 주문 상태를 PAID로 변경
        order.pay();
        orderRepository.save(order);

        // 6. 장바구니 정리
        clearCart(userId, order);

        log.info("결제 승인 완료: paymentKey={}, orderId={}", request.getPaymentKey(), request.getOrderId());
        return PaymentResponse.from(payment);
    }

    /**
     * 결제 취소
     * 1. 결제 정보 검증
     * 2. 토스 API에 취소 요청
     * 3. DB 업데이트
     */
    @Override
    @Transactional
    public PaymentResponse cancelPayment(Long userId, String paymentKey, TossPaymentCancelRequest request) {
        // 1. 결제 정보 조회 및 검증
        Payment payment = paymentRepository.findByPaymentKeyWithOrderItemsForUpdate(paymentKey)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        if (!payment.getOrder().isOwner(userId)) {
            throw new IllegalArgumentException("본인의 결제만 취소할 수 있습니다.");
        }

        // 2. 토스 취소 API 호출
        Map<String, Object> tossResponse = tossPaymentsService.cancelPayment(paymentKey, request);

        // 3. 결제 상태 업데이트
        boolean fullCancel = request.getCancelAmount() == null;

        if (fullCancel) {
            payment.cancel();
            // 주문 취소 + 재고 복원
            Order order = payment.getOrder();
            order.cancel();
        } else {
            payment.partialCancel();
        }

        log.info("결제 취소 완료: paymentKey={}, fullCancel={}", paymentKey, fullCancel);
        return PaymentResponse.from(payment);
    }

    /**
     * 결제 조회
     */
    @Override
    public PaymentResponse getPayment(Long userId, String paymentKey) {
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        if (!payment.getOrder().isOwner(userId)) {
            throw new IllegalArgumentException("본인의 결제만 조회할 수 있습니다.");
        }

        return PaymentResponse.from(payment);
    }

    // ============================================================
    // [내부 헬퍼 메서드]
    // ============================================================

    private void clearCart(Long userId, Order order) {
        List<Long> sourceCartIds = order.getOrderItems().stream()
                .map(item -> item.getSourceCartId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!sourceCartIds.isEmpty()) {
            cartJPARepository.deleteAllByUserIdAndIdIn(userId, sourceCartIds);
            log.info("결제 완료 장바구니 삭제: userId={}, cartCount={}", userId, sourceCartIds.size());
        }
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private String extractReceiptUrl(Map<String, Object> tossResponse) {
        Object receipt = tossResponse.get("receipt");
        if (receipt instanceof Map<?, ?> receiptMap) {
            Object url = receiptMap.get("url");
            return url != null ? url.toString() : null;
        }
        return null;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.toString()).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse date time: value={}", value);
            return null;
        }
    }
}
