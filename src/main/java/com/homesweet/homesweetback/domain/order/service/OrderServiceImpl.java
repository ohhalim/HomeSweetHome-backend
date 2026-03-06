package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.common.exception.OrderNotFoundException;
import com.homesweet.homesweetback.common.exception.StockInsufficientException;
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
import com.homesweet.homesweetback.domain.product.cart.repository.jpa.entity.CartEntity;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 서비스 구현체
 * 장바구니 -> 주문 생성 및 주문 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private static final int RECIPIENT_NAME_MAX_LENGTH = 100;
    private static final int RECIPIENT_PHONE_MAX_LENGTH = 30;
    private static final int SHIPPING_ADDRESS_MAX_LENGTH = 500;
    private static final int SHIPPING_REQUEST_MAX_LENGTH = 500;

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
        if (request == null) {
            throw new IllegalArgumentException("주문 요청이 비어 있습니다.");
        }

        log.info("주문 생성 시작: userId={}, itemCount={}",
                userId, request.getOrderItems() != null ? request.getOrderItems().size() : 0);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        ShippingInfo shippingInfo = normalizeShippingInfo(request);
        List<OrderItemRequest> orderItemRequests = validateCreateOrderRequest(request);
        validateCartItems(userId, orderItemRequests);

        Map<Long, Integer> skuQuantitiesById = mergeOrderQuantities(orderItemRequests);
        Map<Long, Long> sourceCartIdBySku = mapSourceCartIdBySku(orderItemRequests);
        Map<Long, SkuEntity> skuMap = loadSkuMap(skuQuantitiesById.keySet().stream().toList());
        long totalAmount = calculateTotalAmount(skuQuantitiesById, skuMap);

        reserveStock(skuQuantitiesById);

        Order order = Order.builder()
                .user(user)
                .orderNumber(generateOrderNumber())
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .recipientName(shippingInfo.recipientName())
                .recipientPhone(shippingInfo.recipientPhone())
                .shippingAddress(shippingInfo.shippingAddress())
                .shippingRequest(shippingInfo.shippingRequest())
                .build();

        for (Map.Entry<Long, Integer> entry : skuQuantitiesById.entrySet()) {
            SkuEntity sku = skuMap.get(entry.getKey());
            int quantity = entry.getValue();
            long unitPrice = sku.getFinalPrice();

            OrderItem orderItem = OrderItem.builder()
                    .sku(sku)
                    .sourceCartId(sourceCartIdBySku.get(entry.getKey()))
                    .productName(sku.getProduct().getName())
                    .quantity((long) quantity)
                    .price(unitPrice)
                    .build();

            order.addOrderItem(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("주문 생성 완료: orderId={}, orderNumber={}, totalAmount={}",
                savedOrder.getId(), savedOrder.getOrderNumber(), savedOrder.getTotalAmount());

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
     * 취소 시 차감된 재고를 복원합니다.
     */
    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다. orderId=" + orderId));

        if (!order.isOwner(userId)) {
            throw new IllegalArgumentException("본인의 주문만 취소할 수 있습니다.");
        }

        if (!order.isPending()) {
            throw new IllegalStateException("결제 대기 상태의 주문만 취소할 수 있습니다.");
        }

        // 재고 복원
        restoreStock(order);

        order.cancel();
        log.info("주문 취소 완료: orderId={}", orderId);
    }

    /**
     * 주문 항목의 재고를 복원합니다.
     */
    private void restoreStock(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            skuJPARepository.increaseStock(item.getSku().getId(), item.getQuantity());
            log.info("재고 복원: skuId={}, quantity={}", item.getSku().getId(), item.getQuantity());
        }
    }

    private List<OrderItemRequest> validateCreateOrderRequest(CreateOrderRequest request) {
        List<OrderItemRequest> orderItemRequests = request.getOrderItems();
        if (orderItemRequests == null || orderItemRequests.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품이 없습니다.");
        }

        for (OrderItemRequest item : orderItemRequests) {
            if (item.getSkuId() == null) {
                throw new IllegalArgumentException("skuId는 필수입니다.");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
            }
        }

        return orderItemRequests;
    }

    private void validateCartItems(Long userId, List<OrderItemRequest> orderItemRequests) {
        List<Long> cartIds = orderItemRequests.stream()
                .map(OrderItemRequest::getCartId)
                .filter(Objects::nonNull)
                .toList();

        if (cartIds.isEmpty()) {
            return;
        }

        if (cartIds.size() != cartIds.stream().distinct().count()) {
            throw new IllegalArgumentException("중복된 cartId가 포함되어 있습니다.");
        }

        List<CartEntity> cartItems = cartJPARepository.findAllByUserIdAndIdInWithSkuAndProduct(userId, cartIds);
        if (cartItems.size() != cartIds.size()) {
            throw new IllegalArgumentException("유효하지 않은 장바구니 상품이 포함되어 있습니다.");
        }

        Map<Long, CartEntity> cartItemMap = cartItems.stream()
                .collect(Collectors.toMap(CartEntity::getId, cart -> cart));

        for (OrderItemRequest requestItem : orderItemRequests) {
            if (requestItem.getCartId() == null) {
                continue;
            }

            CartEntity cartItem = cartItemMap.get(requestItem.getCartId());
            if (cartItem == null || !cartItem.getSku().getId().equals(requestItem.getSkuId())) {
                throw new IllegalArgumentException("장바구니 상품 정보가 요청과 일치하지 않습니다.");
            }
            if (!cartItem.getQuantity().equals(requestItem.getQuantity())) {
                throw new IllegalArgumentException("장바구니 수량이 최신 상태와 일치하지 않습니다.");
            }
        }
    }

    private Map<Long, Integer> mergeOrderQuantities(List<OrderItemRequest> orderItemRequests) {
        return orderItemRequests.stream()
                .collect(Collectors.groupingBy(
                        OrderItemRequest::getSkuId,
                        LinkedHashMap::new,
                        Collectors.summingInt(OrderItemRequest::getQuantity)
                ));
    }

    private Map<Long, Long> mapSourceCartIdBySku(List<OrderItemRequest> orderItemRequests) {
        Map<Long, Long> sourceCartIdBySku = new LinkedHashMap<>();

        for (OrderItemRequest requestItem : orderItemRequests) {
            if (requestItem.getCartId() == null) {
                continue;
            }

            Long previousCartId = sourceCartIdBySku.putIfAbsent(requestItem.getSkuId(), requestItem.getCartId());
            if (previousCartId != null && !previousCartId.equals(requestItem.getCartId())) {
                throw new IllegalArgumentException("동일 SKU에 여러 장바구니 항목을 사용할 수 없습니다.");
            }
        }

        return sourceCartIdBySku;
    }

    private Map<Long, SkuEntity> loadSkuMap(List<Long> skuIds) {
        List<SkuEntity> skus = skuJPARepository.findAllByIdWithProduct(skuIds);
        if (skus.isEmpty()) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }

        Map<Long, SkuEntity> skuMap = skus.stream()
                .collect(Collectors.toMap(SkuEntity::getId, sku -> sku));
        if (skuMap.size() != skuIds.size()) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }
        return skuMap;
    }

    private long calculateTotalAmount(Map<Long, Integer> skuQuantitiesById, Map<Long, SkuEntity> skuMap) {
        long totalAmount = 0L;
        for (Map.Entry<Long, Integer> entry : skuQuantitiesById.entrySet()) {
            SkuEntity sku = skuMap.get(entry.getKey());
            totalAmount = Math.addExact(totalAmount, sku.calculateTotalPrice(entry.getValue().longValue()));
        }
        return totalAmount;
    }

    private void reserveStock(Map<Long, Integer> skuQuantitiesById) {
        List<Map.Entry<Long, Integer>> deductedEntries = new ArrayList<>();
        try {
            for (Map.Entry<Long, Integer> entry : skuQuantitiesById.entrySet()) {
                Long skuId = entry.getKey();
                long quantity = entry.getValue().longValue();
                int updatedRows = skuJPARepository.decreaseStock(skuId, quantity);
                if (updatedRows == 0) {
                    throw new StockInsufficientException(
                            "재고가 부족합니다. (SKU: " + skuId + ", 요청 수량: " + quantity + ")");
                }
                deductedEntries.add(entry);
            }
        } catch (StockInsufficientException e) {
            for (Map.Entry<Long, Integer> deducted : deductedEntries) {
                skuJPARepository.increaseStock(deducted.getKey(), deducted.getValue().longValue());
            }
            throw e;
        }
    }

    private ShippingInfo normalizeShippingInfo(CreateOrderRequest request) {
        return new ShippingInfo(
                normalizeRequiredText(request.getRecipientName(), "수령인 이름은 필수입니다.",
                        RECIPIENT_NAME_MAX_LENGTH, "수령인 이름은 100자를 초과할 수 없습니다."),
                normalizeRequiredText(request.getRecipientPhone(), "수령인 전화번호는 필수입니다.",
                        RECIPIENT_PHONE_MAX_LENGTH, "수령인 전화번호는 30자를 초과할 수 없습니다."),
                normalizeRequiredText(request.getShippingAddress(), "배송 주소는 필수입니다.",
                        SHIPPING_ADDRESS_MAX_LENGTH, "배송 주소는 500자를 초과할 수 없습니다."),
                normalizeOptionalText(request.getShippingRequest(), SHIPPING_REQUEST_MAX_LENGTH,
                        "배송 요청사항은 500자를 초과할 수 없습니다.")
        );
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeRequiredText(String value, String message, int maxLength, String maxLengthMessage) {
        String normalized = normalizeRequiredText(value, message);
        validateMaxLength(normalized, maxLength, maxLengthMessage);
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength, String maxLengthMessage) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        validateMaxLength(normalized, maxLength, maxLengthMessage);
        return normalized;
    }

    private void validateMaxLength(String value, int maxLength, String message) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 주문번호 생성 (UUID 기반, 토스페이먼츠 orderId로 사용)
     * 최대 64자 제한
     */
    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private record ShippingInfo(
            String recipientName,
            String recipientPhone,
            String shippingAddress,
            String shippingRequest
    ) {
    }
}
