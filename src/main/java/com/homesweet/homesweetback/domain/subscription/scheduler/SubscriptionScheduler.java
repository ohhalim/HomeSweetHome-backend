package com.homesweet.homesweetback.domain.subscription.scheduler;

import com.homesweet.homesweetback.domain.subscription.entity.Subscription;
import com.homesweet.homesweetback.domain.subscription.repository.SubscriptionRepository;
import com.homesweet.homesweetback.domain.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 구독 자동 결제 스케줄러
 * 매일 정해진 시간에 자동 결제 및 만료 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    /**
     * 매일 오전 9시에 자동 결제 실행
     * 다음 결제일이 오늘인 구독자들 자동 결제
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void processAutoPayments() {
        log.info("구독 자동 결제 스케줄러 시작");

        LocalDate today = LocalDate.now();
        List<Subscription> subscriptionsToRenew = 
                subscriptionRepository.findByNextPaymentDateAndStatusActive(today);

        log.info("오늘 결제 예정 구독 수: {}", subscriptionsToRenew.size());

        int successCount = 0;
        int failCount = 0;

        for (Subscription subscription : subscriptionsToRenew) {
            boolean success = subscriptionService.renewSubscription(subscription);
            if (success) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("구독 자동 결제 완료: 성공={}, 실패={}", successCount, failCount);
    }

    /**
     * 매일 자정에 만료된 구독 처리
     * 만료일이 지났는데 아직 ACTIVE인 구독을 EXPIRED로 변경
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void processExpiredSubscriptions() {
        log.info("만료 구독 처리 스케줄러 시작");

        LocalDate today = LocalDate.now();
        List<Subscription> expiredSubscriptions = 
                subscriptionRepository.findExpiredSubscriptions(today);

        log.info("만료 처리 대상 구독 수: {}", expiredSubscriptions.size());

        for (Subscription subscription : expiredSubscriptions) {
            subscription.expire();
            log.info("구독 만료 처리: subscriptionId={}, userId={}", 
                    subscription.getId(), subscription.getUser().getId());
        }

        subscriptionRepository.saveAll(expiredSubscriptions);
        log.info("만료 구독 처리 완료: {} 건", expiredSubscriptions.size());
    }
}
