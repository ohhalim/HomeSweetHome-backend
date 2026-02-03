package com.homesweet.homesweetback.domain.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 결제 엔티티
 */
@Entity
@Table(name = "payments")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "payment_key", nullable = false, length = 200)
    private String paymentKey;

    /**
     * 토스페이먼츠 orderId (주문번호)
     * Order.orderNumber와 동일한 값이 들어감
     */
    @Column(name = "toss_order_id", nullable = false, length = 64)
    private String tossOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false)
    private Long amount;

    @Column(length = 20)
    private String method;

    /**
     * 결제 요청 시간 (토스 API 응답의 requestedAt)
     */
    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    /**
     * 결제 승인 시간 (토스 API 응답의 approvedAt)
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * 영수증 URL (토스 API 응답의 receipt.url)
     */
    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ===== 비즈니스 메서드 =====

    public void complete(String method, LocalDateTime approvedAt, String receiptUrl) {
        this.status = PaymentStatus.DONE;
        this.method = method;
        this.approvedAt = approvedAt;
        this.receiptUrl = receiptUrl;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }

    public void partialCancel() {
        this.status = PaymentStatus.PARTIAL_CANCELED;
    }
}
