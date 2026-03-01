package com.homesweet.homesweetback.domain.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCancellationReconcileScheduler {

    private final PaymentCancellationTransactionalService paymentCancellationTransactionalService;
    private final TossPaymentsService tossPaymentsService;

    @Value("${order.scheduler.cancel-reconcile-batch-size:50}")
    private int batchSize;

    @Scheduled(
            initialDelayString = "${order.scheduler.cancel-reconcile-initial-delay-ms:45000}",
            fixedDelayString = "${order.scheduler.cancel-reconcile-delay-ms:60000}"
    )
    public void reconcileCancellationStates() {
        List<String> paymentKeys = paymentCancellationTransactionalService.findPendingCancellationPaymentKeys(batchSize);
        if (paymentKeys.isEmpty()) {
            return;
        }

        int updated = 0;
        for (String paymentKey : paymentKeys) {
            try {
                Map<String, Object> tossPayment = tossPaymentsService.getPaymentByPaymentKey(paymentKey);
                String tossStatus = tossPayment.get("status") != null ? tossPayment.get("status").toString() : null;
                boolean changed = paymentCancellationTransactionalService.reconcileByTossStatus(paymentKey, tossStatus);
                if (changed) {
                    updated++;
                }
            } catch (Exception e) {
                log.warn("취소 정합성 점검 실패: paymentKey={}", paymentKey, e);
            }
        }

        log.info("취소 정합성 점검 완료: candidates={}, updated={}", paymentKeys.size(), updated);
    }
}
