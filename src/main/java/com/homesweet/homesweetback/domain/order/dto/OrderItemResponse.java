package com.homesweet.homesweetback.domain.order.dto;

import com.homesweet.homesweetback.domain.order.entity.OrderItem;
import lombok.Builder;
import lombok.Getter;

/**
 * 주문 상품 응답 DTO
 */
@Getter
@Builder
public class OrderItemResponse {

    private Long orderItemId;
    private Long skuId;
    private String productName;
    private Long quantity;
    private Long unitPrice;
    private Long totalPrice;

    public static OrderItemResponse from(OrderItem orderItem) {
        return OrderItemResponse.builder()
                .orderItemId(orderItem.getId())
                .skuId(orderItem.getSku().getId())
                .productName(orderItem.getProductName())
                .quantity(orderItem.getQuantity())
                .unitPrice(orderItem.getPrice())
                .totalPrice(orderItem.getTotalPrice())
                .build();
    }
}
