package com.homesweet.homesweetback.domain.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderExpirationScheduler {

    private final PendingOrderExpirationService pendingOrderExpirationService;

    @Value("${order.scheduler.pending-expire-after-minutes:15}")
    private long expireAfterMinutes;

    @Value("${order.scheduler.pending-expire-batch-size:100}")
    private int batchSize;

    @Value("${order.scheduler.pending-expire-max-batches:5}")
    private int maxBatches;

    @Scheduled(
            initialDelayString = "${order.scheduler.pending-expire-initial-delay-ms:30000}",
            fixedDelayString = "${order.scheduler.pending-expire-delay-ms:60000}"
    )
    public void expirePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expireAfterMinutes);
        int totalExpired = 0;

        for (int i = 0; i < Math.max(1, maxBatches); i++) {
            int expired = pendingOrderExpirationService.expirePendingOrders(cutoff, batchSize);
            totalExpired += expired;

            if (expired < Math.max(1, batchSize)) {
                break;
            }
        }

        if (totalExpired > 0) {
            log.info("PENDING 주문 만료 처리 완료: expired={}, cutoff={}", totalExpired, cutoff);
        }
    }
}
