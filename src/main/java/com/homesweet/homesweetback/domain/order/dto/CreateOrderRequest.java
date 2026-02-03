package com.homesweet.homesweetback.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 생성 요청 DTO
 * 프론트엔드에서 장바구니 상품 정보와 배송 정보를 함께 전송
 */
@Getter
@NoArgsConstructor
public class CreateOrderRequest {

    /**
     * 주문 상품 목록
     */
    @NotEmpty(message = "최소 하나 이상의 상품이 필요합니다.")
    private List<OrderItemRequest> orderItems;

    /**
     * 수령인 이름
     */
    @NotBlank(message = "수령인 이름은 필수입니다.")
    private String recipientName;

    /**
     * 수령인 전화번호
     */
    @NotBlank(message = "수령인 전화번호는 필수입니다.")
    private String recipientPhone;

    /**
     * 배송 주소
     */
    @NotBlank(message = "배송 주소는 필수입니다.")
    private String shippingAddress;

    /**
     * 배송 요청사항
     */
    private String shippingRequest;

    /**
     * orderItems에서 cartId 목록 추출
     */
    public List<Long> getCartIds() {
        if (orderItems == null) {
            return List.of();
        }
        return orderItems.stream()
                .map(OrderItemRequest::getCartId)
                .collect(Collectors.toList());
    }

    /**
     * 주문 상품 요청 DTO
     */
    @Getter
    @NoArgsConstructor
    public static class OrderItemRequest {
        private Long cartId;
        private Long skuId;
        private Integer quantity;
        private String productName;
        private Long price;
    }
}
