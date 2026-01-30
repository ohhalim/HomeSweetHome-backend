package com.homesweet.homesweetback.domain.subscription.entity;

import com.homesweet.homesweetback.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 구독 엔티티
 * 사용자의 프리미엄 구독 정보를 저장
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    /**
     * 토스페이먼츠 빌링키
     * 자동결제에 사용되는 고유 키
     */
    @Column(name = "billing_key", nullable = false, length = 200)
    private String billingKey;

    /**
     * 고객 고유 키 (가맹점에서 생성)
     * 빌링키 발급/결제 시 사용
     */
    @Column(name = "customer_key", nullable = false, length = 50)
    private String customerKey;

    /**
     * 구독 시작일
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * 현재 구독 기간 만료일
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * 다음 결제 예정일
     */
    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== 비즈니스 메서드 =====

    /**
     * 구독이 활성 상태인지 확인
     */
    public boolean isActive() {
        return this.status == SubscriptionStatus.ACTIVE
                && LocalDate.now().isBefore(this.endDate.plusDays(1));
    }

    /**
     * 구독 갱신 (결제 성공 시 호출)
     */
    public void renew() {
        this.endDate = this.endDate.plusDays(plan.getDurationDays());
        this.nextPaymentDate = this.endDate;
        this.status = SubscriptionStatus.ACTIVE;
    }

    /**
     * 구독 취소
     */
    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
        this.nextPaymentDate = null;
    }

    /**
     * 구독 만료 처리
     */
    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
        this.nextPaymentDate = null;
    }

    /**
     * 구독 소유자 확인
     */
    public boolean isOwner(Long userId) {
        return this.user.getId().equals(userId);
    }
}
