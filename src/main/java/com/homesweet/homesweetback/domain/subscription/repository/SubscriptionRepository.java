package com.homesweet.homesweetback.domain.subscription.repository;

import com.homesweet.homesweetback.domain.subscription.entity.Subscription;
import com.homesweet.homesweetback.domain.subscription.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Long userId);

    Optional<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    boolean existsByUserIdAndStatus(Long userId, SubscriptionStatus status);

    /**
     * 다음 결제일이 특정 날짜인 활성 구독 조회
     * 스케줄러에서 자동 결제 시 사용
     */
    @Query("SELECT s FROM Subscription s WHERE s.nextPaymentDate = :date AND s.status = 'ACTIVE'")
    List<Subscription> findByNextPaymentDateAndStatusActive(@Param("date") LocalDate date);

    /**
     * 만료일이 지난 활성 구독 조회
     * 만료 처리용
     */
    @Query("SELECT s FROM Subscription s WHERE s.endDate < :date AND s.status = 'ACTIVE'")
    List<Subscription> findExpiredSubscriptions(@Param("date") LocalDate date);
}
