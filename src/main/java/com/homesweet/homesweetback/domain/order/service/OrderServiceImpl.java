package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.common.exception.OrderNotFoundException;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.order.dto.CreateOrderRequest;
import com.homesweet.homesweetback.domain.order.dto.CreateOrderRequest.OrderItemRequest;
import com.homesweet.homesweetback.domain.order.dto.OrderResponse;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderItem;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.product.cart.repository.jpa.CartJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

/**
 * 주문 서비스 구현체
 * 장바구니 -> 주문 생성 및 주문 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartJPARepository cartJPARepository;
    private final SkuJPARepository skuJPARepository;
    private final UserRepository userRepository;

    /**
     * 주문 생성
     * 프론트엔드에서 전달된 상품 정보(skuId, quantity)를 기반으로 주문 생성
     */
    @Override
    @Transactional
    public OrderResponse createFromCart(Long userId, CreateOrderRequest request) {
        log.info("주문 생성 시작: userId={}, orderItems={}", userId, request.getOrderItems());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<OrderItemRequest> orderItemRequests = request.getOrderItems();
        if (orderItemRequests == null || orderItemRequests.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품이 없습니다.");
        }

        // 내부 호출 대비 방어 검증 (컨트롤러 경유 시 DTO @NotNull/@Positive가 1차 차단)
        for (OrderItemRequest item : orderItemRequests) {
            if (item.getSkuId() == null) {
                throw new IllegalArgumentException("skuId는 필수입니다.");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
            }
        }

        Map<Long, Integer> skuQuantitiesById = orderItemRequests.stream()
                .collect(Collectors.groupingBy(
                        OrderItemRequest::getSkuId,
                        LinkedHashMap::new,
                        Collectors.summingInt(OrderItemRequest::getQuantity)
                ));

        List<Long> skuIds = skuQuantitiesById.keySet().stream().toList();

        // SKU 조회 (Product fetch join)
        List<SkuEntity> skus = skuJPARepository.findAllByIdWithProduct(skuIds);

        if (skus.isEmpty()) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }

        // SKU ID -> SKU Entity 매핑
        Map<Long, SkuEntity> skuMap = skus.stream()
                .collect(Collectors.toMap(SkuEntity::getId, s -> s));
        if (skuMap.size() != skuQuantitiesById.size()) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }

        // 총액 계산
        long totalAmount = 0L;
        for (Map.Entry<Long, Integer> entry : skuQuantitiesById.entrySet()) {
            SkuEntity sku = skuMap.get(entry.getKey());
            long unitPrice = sku.getFinalPrice();
            totalAmount += unitPrice * entry.getValue();
        }

        // 주문 생성
        String orderNumber = generateOrderNumber();
        Order order = Order.builder()
                .user(user)
                .orderNumber(orderNumber)
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .build();

        // 주문 상품 추가
        for (Map.Entry<Long, Integer> entry : skuQuantitiesById.entrySet()) {
            SkuEntity sku = skuMap.get(entry.getKey());
            int quantity = entry.getValue();
            long unitPrice = sku.getFinalPrice();

            OrderItem orderItem = OrderItem.builder()
                    .sku(sku)
                    .quantity((long) quantity)
                    .price(unitPrice)
                    .build();

            order.addOrderItem(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("주문 생성 완료: orderId={}, orderNumber={}, totalAmount={}",
                savedOrder.getId(), savedOrder.getOrderNumber(), savedOrder.getTotalAmount());

        // 장바구니에서 주문된 상품 삭제
        List<Long> cartIds = request.getCartIds();
        if (cartIds != null && !cartIds.isEmpty()) {
            cartJPARepository.deleteAllByUserIdAndIdIn(userId, cartIds);
            log.info("장바구니에서 상품 삭제 완료: userId={}, cartIds={}", userId, cartIds);
        }

        return OrderResponse.from(savedOrder);
    }

    /**
     * 주문 단건 조회
     */
    @Override
    public OrderResponse getOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다. orderId=" + orderId));

        if (!order.isOwner(userId)) {
            throw new IllegalArgumentException("본인의 주문만 조회할 수 있습니다.");
        }

        return OrderResponse.from(order);
    }

    /**
     * 내 주문 목록 조회
     */
    @Override
    public List<OrderResponse> getMyOrders(Long userId) {
        List<Order> orders = orderRepository.findByUserIdWithItemsAndProduct(userId);
        return orders.stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * 주문 취소 (결제 전 PENDING 상태에서만 가능)
     */
    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다. orderId=" + orderId));

        if (!order.isOwner(userId)) {
            throw new IllegalArgumentException("본인의 주문만 취소할 수 있습니다.");
        }

        if (!order.isPending()) {
            throw new IllegalStateException("결제 대기 상태의 주문만 취소할 수 있습니다.");
        }

        order.cancel();
        log.info("주문 취소 완료: orderId={}", orderId);
    }

    /**
     * 주문번호 생성 (UUID 기반, 토스페이먼츠 orderId로 사용)
     * 최대 64자 제한
     */
    private String generateOrderNumber() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }
}
