package com.homesweet.homesweetback.domain.subscription.service;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.subscription.dto.CreateSubscriptionRequest;
import com.homesweet.homesweetback.domain.subscription.dto.SubscriptionResponse;
import com.homesweet.homesweetback.domain.subscription.entity.Subscription;
import com.homesweet.homesweetback.domain.subscription.entity.SubscriptionPlan;
import com.homesweet.homesweetback.domain.subscription.entity.SubscriptionStatus;
import com.homesweet.homesweetback.domain.subscription.exception.SubscriptionRequiredException;
import com.homesweet.homesweetback.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * 구독 서비스
 * 프리미엄 커뮤니티 구독 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final TossBillingService tossBillingService;

    /**
     * 구독 생성 (첫 달 무료)
     * 빌링키 발급 후 첫 달은 무료로 시작
     */
    @Transactional
    public SubscriptionResponse createSubscription(Long userId, CreateSubscriptionRequest request) {
        log.info("구독 생성 시작: userId={}", userId);

        // 이미 구독 중인지 확인
        if (subscriptionRepository.existsByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)) {
            throw new IllegalStateException("이미 구독 중입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 토스에서 빌링키 발급
        Map<String, Object> billingResponse = tossBillingService.issueBillingKey(
                request.getAuthKey(),
                request.getCustomerKey());

        String billingKey = (String) billingResponse.get("billingKey");

        // 첫 달 무료 - 시작일로부터 30일 후 첫 결제
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(SubscriptionPlan.PREMIUM_MONTHLY.getDurationDays());

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(SubscriptionPlan.PREMIUM_MONTHLY)
                .status(SubscriptionStatus.ACTIVE)
                .billingKey(billingKey)
                .customerKey(request.getCustomerKey())
                .startDate(today)
                .endDate(endDate)
                .nextPaymentDate(endDate) // 첫 달 무료이므로 30일 후 첫 결제
                .build();

        subscriptionRepository.save(subscription);
        log.info("구독 생성 완료 (첫 달 무료): userId={}, 만료일={}", userId, endDate);

        return SubscriptionResponse.from(subscription);
    }

    /**
     * 내 구독 정보 조회
     */
    public SubscriptionResponse getMySubscription(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new SubscriptionRequiredException("구독 정보가 없습니다."));

        return SubscriptionResponse.from(subscription);
    }

    /**
     * 구독 취소
     * 다음 결제일부터 자동결제 중지, 현재 기간은 계속 이용 가능
     */
    @Transactional
    public void cancelSubscription(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("활성화된 구독이 없습니다."));

        subscription.cancel();
        log.info("구독 취소 완료: userId={}, 남은 기간 만료일={}", userId, subscription.getEndDate());
    }

    /**
     * 구독 갱신 (자동결제)
     * 스케줄러에서 호출
     */
    @Transactional
    public boolean renewSubscription(Subscription subscription) {
        log.info("구독 갱신 시도: subscriptionId={}", subscription.getId());

        try {
            String orderId = "SUB_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            String orderName = subscription.getPlan().getDisplayName();
            Long amount = subscription.getPlan().getPrice();

            // 빌링키로 결제 요청
            tossBillingService.requestBillingPayment(
                    subscription.getBillingKey(),
                    subscription.getCustomerKey(),
                    amount,
                    orderId,
                    orderName);

            // 구독 갱신 (다음 30일 연장)
            subscription.renew();
            log.info("구독 갱신 성공: subscriptionId={}, 새 만료일={}", 
                    subscription.getId(), subscription.getEndDate());
            return true;
        } catch (Exception e) {
            log.error("구독 갱신 실패: subscriptionId={}, error={}", 
                    subscription.getId(), e.getMessage());
            // 결제 실패 시 만료 처리
            subscription.expire();
            return false;
        }
    }

    /**
     * 사용자가 활성 구독자인지 확인
     * 커뮤니티 접근 권한 체크에 사용
     */
    public boolean isActiveSubscriber(Long userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .map(Subscription::isActive)
                .orElse(false);
    }

    /**
     * 구독 필수 기능 접근 검증
     * 비구독자면 예외 발생
     */
    public void validateSubscription(Long userId) {
        if (!isActiveSubscriber(userId)) {
            throw new SubscriptionRequiredException();
        }
    }
}
