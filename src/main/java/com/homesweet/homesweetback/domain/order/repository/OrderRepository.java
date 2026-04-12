package com.homesweet.homesweetback.domain.order.repository;

import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.sku " +
            "WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItemsForUpdate(@Param("orderNumber") String orderNumber);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.sku " +
            "WHERE o.user.id = :userId " +
            "ORDER BY o.createdAt DESC")
    List<Order> findByUserIdWithItemsAndProduct(@Param("userId") Long userId);

    @Query("SELECT o.id FROM Order o " +
            "WHERE o.user.id = :userId " +
            "ORDER BY o.createdAt DESC")
    List<Long> findIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.sku " +
            "WHERE o.id IN :orderIds " +
            "ORDER BY o.createdAt DESC")
    List<Order> findByIdInWithItemsAndProduct(@Param("orderIds") List<Long> orderIds);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.sku " +
            "WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.sku " +
            "WHERE o.id = :orderId")
    Optional<Order> findByIdWithItemsForUpdate(@Param("orderId") Long orderId);

    @Query("SELECT o.id FROM Order o " +
            "WHERE o.status = :status " +
            "AND o.createdAt <= :cutoff " +
            "ORDER BY o.id ASC")
    List<Long> findExpiredOrderIds(@Param("status") OrderStatus status,
                                   @Param("cutoff") LocalDateTime cutoff,
                                   Pageable pageable);
}
