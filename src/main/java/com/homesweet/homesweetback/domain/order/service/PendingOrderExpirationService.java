package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderItem;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PendingOrderExpirationService {

    private final OrderRepository orderRepository;
    private final SkuJPARepository skuJPARepository;

    @Transactional
    public int expirePendingOrders(LocalDateTime cutoff, int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        List<Long> expiredCandidateIds = orderRepository.findExpiredOrderIds(
                OrderStatus.PENDING,
                cutoff,
                PageRequest.of(0, safeBatchSize));

        int expiredCount = 0;
        for (Long orderId : expiredCandidateIds) {
            if (expireSingleOrder(orderId)) {
                expiredCount++;
            }
        }
        return expiredCount;
    }

    private boolean expireSingleOrder(Long orderId) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId).orElse(null);
        if (order == null || !order.isPending()) {
            return false;
        }

        for (OrderItem item : order.getOrderItems()) {
            skuJPARepository.increaseStock(item.getSku().getId(), item.getQuantity());
            log.info("만료 주문 재고 복원: orderId={}, skuId={}, quantity={}",
                    orderId, item.getSku().getId(), item.getQuantity());
        }

        order.cancel();
        log.info("만료 주문 취소 완료: orderId={}", orderId);
        return true;
    }
}
