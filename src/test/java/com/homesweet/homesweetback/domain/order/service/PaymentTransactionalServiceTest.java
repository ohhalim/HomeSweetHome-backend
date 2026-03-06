package com.homesweet.homesweetback.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.entity.UserRole;
import com.homesweet.homesweetback.domain.order.dto.PaymentResponse;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderItem;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.order.entity.Payment;
import com.homesweet.homesweetback.domain.order.entity.PaymentStatus;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.order.repository.PaymentRepository;
import com.homesweet.homesweetback.domain.product.cart.repository.jpa.CartJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentTransactionalService 테스트")
class PaymentTransactionalServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartJPARepository cartJPARepository;

    @InjectMocks
    private PaymentTransactionalService paymentTransactionalService;

    @Test
    @DisplayName("결제 완료 시 주문 생성에 사용한 장바구니만 삭제")
    void persistConfirmedPayment_DeletesOnlySourceCartIds() {
        Long userId = 1L;
        String orderNumber = "ORD-TEST-001";
        Order order = createPendingOrder(
                10L,
                userId,
                orderNumber,
                30000L,
                List.of(
                        createOrderItem(100L, 1L, "상품A", 2L, 10000L),
                        createOrderItem(101L, 2L, "상품B", 1L, 10000L)
                )
        );

        TossPaymentConfirmRequest request = new TossPaymentConfirmRequest("payment-key", orderNumber, 30000L);
        Map<String, Object> tossResponse = Map.of(
                "requestedAt", "2026-03-06T05:00:00+09:00",
                "approvedAt", "2026-03-06T05:00:05+09:00",
                "method", "카드"
        );

        given(orderRepository.findByOrderNumberWithItemsForUpdate(orderNumber)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrder(order)).willReturn(Optional.empty());
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentTransactionalService.persistConfirmedPayment(userId, request, tossResponse);

        assertThat(response.getOrderId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(cartJPARepository).deleteAllByUserIdAndIdIn(userId, List.of(100L, 101L));
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    @DisplayName("장바구니 기반이 아닌 주문은 결제 완료 후 장바구니를 건드리지 않음")
    void persistConfirmedPayment_DoesNotDeleteCartWhenSourceCartIdsAbsent() {
        Long userId = 1L;
        String orderNumber = "ORD-TEST-002";
        Order order = createPendingOrder(
                11L,
                userId,
                orderNumber,
                15000L,
                List.of(createOrderItem(null, 3L, "바로구매상품", 1L, 15000L))
        );

        TossPaymentConfirmRequest request = new TossPaymentConfirmRequest("payment-key-2", orderNumber, 15000L);
        Map<String, Object> tossResponse = Map.of(
                "requestedAt", "2026-03-06T05:10:00+09:00",
                "approvedAt", "2026-03-06T05:10:05+09:00",
                "method", "카드"
        );

        given(orderRepository.findByOrderNumberWithItemsForUpdate(orderNumber)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrder(order)).willReturn(Optional.empty());
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentTransactionalService.persistConfirmedPayment(userId, request, tossResponse);

        assertThat(response.getOrderId()).isEqualTo(11L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(cartJPARepository, never()).deleteAllByUserIdAndIdIn(any(), any());
    }

    private Order createPendingOrder(Long orderId, Long userId, String orderNumber, Long totalAmount, List<OrderItem> items) {
        User user = User.builder()
                .email("test@test.com")
                .name("테스트유저")
                .role(UserRole.USER)
                .build();
        user.setId(userId);

        Order order = Order.builder()
                .id(orderId)
                .user(user)
                .orderNumber(orderNumber)
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .recipientName("홍길동")
                .recipientPhone("01012345678")
                .shippingAddress("서울시 강남구")
                .build();

        items.forEach(order::addOrderItem);
        return order;
    }

    private OrderItem createOrderItem(Long sourceCartId, Long skuId, String productName, Long quantity, Long price) {
        SkuEntity sku = mock(SkuEntity.class);

        return OrderItem.builder()
                .sku(sku)
                .sourceCartId(sourceCartId)
                .productName(productName)
                .quantity(quantity)
                .price(price)
                .build();
    }
}
