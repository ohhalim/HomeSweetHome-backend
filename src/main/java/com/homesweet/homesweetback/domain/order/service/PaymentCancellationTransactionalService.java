package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.domain.order.dto.PaymentResponse;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderItem;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.order.entity.Payment;
import com.homesweet.homesweetback.domain.order.entity.PaymentStatus;
import com.homesweet.homesweetback.domain.order.repository.PaymentRepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentCancellationTransactionalService {

    private final PaymentRepository paymentRepository;
    private final SkuJPARepository skuJPARepository;

    @Transactional
    public void markCancelRequested(Long userId, String paymentKey) {
        Payment payment = findPaymentForUpdate(paymentKey);
        validateOwner(payment, userId);

        PaymentStatus status = payment.getStatus();
        if (status == PaymentStatus.CANCELLED || status == PaymentStatus.PARTIAL_CANCELED) {
            throw new IllegalStateException("이미 취소가 완료된 결제입니다.");
        }
        if (status == PaymentStatus.CANCEL_REQUESTED) {
            return;
        }
        if (status != PaymentStatus.DONE && status != PaymentStatus.CANCEL_FAILED) {
            throw new IllegalStateException("취소 가능한 결제 상태가 아닙니다.");
        }

        payment.requestCancel();
    }

    @Transactional
    public PaymentResponse finalizeCancelSuccess(String paymentKey, boolean fullCancel) {
        Payment payment = findPaymentForUpdate(paymentKey);

        if (fullCancel) {
            if (payment.getStatus() == PaymentStatus.CANCELLED) {
                return PaymentResponse.from(payment);
            }
            payment.cancel();
            cancelOrderAndRestoreStockIfNeeded(payment.getOrder());
            return PaymentResponse.from(payment);
        }

        if (payment.getStatus() != PaymentStatus.PARTIAL_CANCELED) {
            payment.partialCancel();
        }
        return PaymentResponse.from(payment);
    }

    @Transactional
    public void markCancelFailed(String paymentKey) {
        Payment payment = findPaymentForUpdate(paymentKey);
        if (payment.getStatus() == PaymentStatus.CANCEL_REQUESTED) {
            payment.cancelFailed();
        }
    }

    public List<String> findPendingCancellationPaymentKeys(int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 100));
        return paymentRepository.findTop100ByStatusInOrderByIdAsc(
                        List.of(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCEL_FAILED))
                .stream()
                .limit(safeBatchSize)
                .map(Payment::getPaymentKey)
                .toList();
    }

    @Transactional
    public boolean reconcileByTossStatus(String paymentKey, String tossStatus) {
        String normalizedStatus = normalizeStatus(tossStatus);

        if ("CANCELED".equals(normalizedStatus) || "CANCELLED".equals(normalizedStatus)) {
            finalizeCancelSuccess(paymentKey, true);
            return true;
        }
        if ("PARTIAL_CANCELED".equals(normalizedStatus) || "PARTIAL_CANCELLED".equals(normalizedStatus)) {
            finalizeCancelSuccess(paymentKey, false);
            return true;
        }
        if ("DONE".equals(normalizedStatus) || "READY".equals(normalizedStatus) || "IN_PROGRESS".equals(normalizedStatus)) {
            markCancelFailed(paymentKey);
            return false;
        }

        log.info("취소 정합성 점검 스킵: paymentKey={}, tossStatus={}", paymentKey, normalizedStatus);
        return false;
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private Payment findPaymentForUpdate(String paymentKey) {
        return paymentRepository.findByPaymentKeyWithOrderItemsForUpdate(paymentKey)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
    }

    private void validateOwner(Payment payment, Long userId) {
        if (!payment.getOrder().isOwner(userId)) {
            throw new IllegalArgumentException("본인의 결제만 취소할 수 있습니다.");
        }
    }

    private void cancelOrderAndRestoreStockIfNeeded(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        order.cancel();
        for (OrderItem item : order.getOrderItems()) {
            skuJPARepository.increaseStock(item.getSku().getId(), item.getQuantity());
            log.info("결제 취소 재고 복원: skuId={}, quantity={}",
                    item.getSku().getId(), item.getQuantity());
        }
    }
}
