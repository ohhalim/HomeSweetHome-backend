package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.domain.order.dto.CreateOrderRequest;
import com.homesweet.homesweetback.domain.order.dto.OrderResponse;

import java.util.List;

/**
 * 주문 서비스 인터페이스
 */
public interface OrderService {

    /**
     * 장바구니에서 주문 생성
     * 토스페이먼츠 결제창 호출 전에 주문을 먼저 생성
     *
     * @param userId 사용자 ID
     * @param request 주문 생성 요청 (장바구니 ID 목록)
     * @return 생성된 주문 정보 (orderId, orderNumber, totalAmount 포함)
     */
    OrderResponse createFromCart(Long userId, CreateOrderRequest request);

    /**
     * 주문 단건 조회
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID (권한 확인용)
     * @return 주문 정보
     */
    OrderResponse getOrder(Long orderId, Long userId);

    /**
     * 내 주문 목록 조회
     *
     * @param userId 사용자 ID
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 주문 목록
     */
    List<OrderResponse> getMyOrders(Long userId, int page, int size);

    /**
     * 주문 취소 (결제 전 상태에서만 가능)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID (권한 확인용)
     */
    void cancelOrder(Long orderId, Long userId);
}
