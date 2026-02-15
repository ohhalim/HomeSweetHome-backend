package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.common.exception.OrderNotFoundException;
import com.homesweet.homesweetback.common.exception.PaymentMismatchException;
import com.homesweet.homesweetback.domain.order.dto.PaymentResponse;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentTransactionalService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CartJPARepository cartJPARepository;

    @Transactional
    public PaymentResponse persistConfirmedPayment(Long userId, TossPaymentConfirmRequest request,
                                                   Map<String, Object> tossResponse) {
        Order order = orderRepository.findByOrderNumberWithItemsForUpdate(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다. orderId=" + request.getOrderId()));

        if (!order.isOwner(userId)) {
            throw new IllegalArgumentException("본인의 주문만 결제할 수 있습니다.");
        }

        Optional<Payment> existingPaymentOpt = paymentRepository.findByOrder(order);
        if (existingPaymentOpt.isPresent()) {
            Payment existing = existingPaymentOpt.get();
            if (Objects.equals(existing.getPaymentKey(), request.getPaymentKey())) {
                return PaymentResponse.from(existing);
            }
            throw new IllegalStateException("이미 결제 요청이 처리 중이거나 완료된 주문입니다.");
        }

        if (!order.getTotalAmount().equals(request.getAmount())) {
            throw new PaymentMismatchException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }

        if (!order.isPending()) {
            throw new IllegalStateException("결제 가능한 상태가 아닙니다.");
        }

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

        order.pay();
        orderRepository.save(order);

        List<Long> purchasedSkuIds = order.getOrderItems().stream()
                .map(item -> item.getSku().getId())
                .toList();
        cartJPARepository.deleteCartItemNative(userId, purchasedSkuIds);

        return PaymentResponse.from(payment);
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
            log.warn("Failed to parse date time from payment response. value={}", value);
            return null;
        }
    }
}
