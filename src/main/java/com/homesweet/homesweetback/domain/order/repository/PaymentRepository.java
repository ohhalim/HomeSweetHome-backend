package com.homesweet.homesweetback.domain.order.repository;

import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.Payment;
import com.homesweet.homesweetback.domain.order.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder(Order order);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    boolean existsByPaymentKey(String paymentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT p FROM Payment p " +
            "JOIN FETCH p.order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.sku " +
            "WHERE p.paymentKey = :paymentKey")
    Optional<Payment> findByPaymentKeyWithOrderItemsForUpdate(@Param("paymentKey") String paymentKey);

    List<Payment> findTop100ByStatusInOrderByIdAsc(List<PaymentStatus> statuses);
}
