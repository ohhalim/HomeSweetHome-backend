package com.homesweet.homesweetback.domain.order.dto;

import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 응답 DTO
 * 토스페이먼츠 결제창 호출 시 필요한 orderId, amount 포함
 */
@Getter
@Builder
public class OrderResponse {

    private Long orderId;

    /**
     * 토스페이먼츠 orderId로 사용될 주문번호
     */
    private String orderNumber;

    /**
     * 주문명 (예: "토스 티셔츠 외 2건")
     */
    private String orderName;

    private OrderStatus status;

    /**
     * 총 결제 금액 (토스페이먼츠 amount)
     */
    private Long totalAmount;

    private String recipientName;

    private String recipientPhone;

    private String shippingAddress;

    private String shippingRequest;

    private List<OrderItemResponse> orderItems;

    private LocalDateTime createdAt;

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        String orderName = generateOrderName(order);

        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderName(orderName)
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .shippingAddress(order.getShippingAddress())
                .shippingRequest(order.getShippingRequest())
                .orderItems(items)
                .createdAt(order.getCreatedAt())
                .build();
    }

    /**
     * 주문명 생성 (토스페이먼츠 orderName 형식)
     * 예: "상품명 외 2건" 또는 "상품명"
     */
    private static String generateOrderName(Order order) {
        if (order.getOrderItems().isEmpty()) {
            return "주문";
        }

        String firstProductName = order.getOrderItems().get(0).getProductName();
        if (firstProductName == null || firstProductName.isBlank()) {
            return "주문";
        }
        int itemCount = order.getOrderItems().size();

        if (itemCount == 1) {
            return firstProductName;
        }
        return firstProductName + " 외 " + (itemCount - 1) + "건";
    }
}
