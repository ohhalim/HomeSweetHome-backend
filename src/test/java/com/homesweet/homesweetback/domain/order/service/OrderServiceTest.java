package com.homesweet.homesweetback.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.homesweet.homesweetback.common.exception.OrderNotFoundException;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.entity.UserRole;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.order.dto.CreateOrderRequest;
import com.homesweet.homesweetback.domain.order.dto.OrderResponse;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.product.cart.repository.jpa.CartJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.ProductEntity;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;

/**
 * OrderService 단위 테스트
 * 
 * 테스트 케이스:
 * 1. 주문 생성 - 성공
 * 2. 주문 생성 - 실패 (장바구니 없음)
 * 3. 주문 생성 - 실패 (다른 사용자의 장바구니)
 * 4. 주문 조회 - 성공
 * 5. 주문 조회 - 실패 (주문 없음)
 * 6. 주문 조회 - 실패 (권한 없음)
 * 7. 주문 목록 조회 - 성공
 * 8. 주문 취소 - 성공
 * 9. 주문 취소 - 실패 (PENDING 상태 아님)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 테스트")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartJPARepository cartJPARepository;

    @Mock
    private SkuJPARepository skuJPARepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    // ===== 테스트 데이터 생성 헬퍼 메서드 =====

    private User createTestUser(Long userId) {
        User user = User.builder()
                .email("test@test.com")
                .name("테스트유저")
                .role(UserRole.USER)
                .build();
        user.setId(userId);
        return user;
    }

    private Order createTestOrder(Long orderId, User user, OrderStatus status) {
        return Order.builder()
                .id(orderId)
                .user(user)
                .orderNumber("TEST-ORDER-001")
                .status(status)
                .totalAmount(50000L)
                .build();
    }

    private SkuEntity createTestSku(Long skuId, Long price) {
        ProductEntity product = mock(ProductEntity.class);
        given(product.getName()).willReturn("테스트상품" + skuId);

        SkuEntity sku = mock(SkuEntity.class);
        given(sku.getId()).willReturn(skuId);
        given(sku.getFinalPrice()).willReturn(price);
        given(sku.getProduct()).willReturn(product);
        return sku;
    }

    private CreateOrderRequest createRequest(List<CreateOrderRequest.OrderItemRequest> items) {
        CreateOrderRequest request = new CreateOrderRequest();
        setField(request, "orderItems", items);
        return request;
    }

    private CreateOrderRequest.OrderItemRequest createOrderItem(Long cartId, Long skuId, Integer quantity) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        setField(item, "cartId", cartId);
        setField(item, "skuId", skuId);
        setField(item, "quantity", quantity);
        return item;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // ===== 주문 생성 테스트 =====

    @Nested
    @DisplayName("주문 생성 테스트")
    class CreateOrderTest {

        @Test
        @DisplayName("주문 생성 성공")
        void createOrder_Success() {
            // given
            Long userId = 1L;
            CreateOrderRequest request = createRequest(List.of(
                    createOrderItem(1L, 1L, 2),
                    createOrderItem(2L, 2L, 1)
            ));

            //테스트용 가짜 user 객체 생성
            User user = createTestUser(userId);

            SkuEntity sku1 = createTestSku(1L, 10000L);
            SkuEntity sku2 = createTestSku(2L, 15000L);

            // userRepository.findById(1L) 호출되면 ->user 반환해라
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(skuJPARepository.findAllByIdWithProduct(List.of(1L, 2L))).willReturn(List.of(sku1, sku2));
            // orderRepository.save() 호출되면 -> 전달받은 order 그대로 반환
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                return invocation.getArgument(0);
            });
            // when
            // 실제 테스트 대상 메서드 호출 - 주문 생성
            OrderResponse response = orderService.createFromCart(userId, request);

            // then
            // 응답이 null이 아닌지 확인
            assertThat(response).isNotNull();
            assertThat(response.getTotalAmount()).isEqualTo(35000L);
            verify(orderRepository, times(1)).save(any(Order.class));

        }

        @Test
        @DisplayName("주문 생성 실패 - 상품 없음")
        void createOrder_Fail_EmptyCart() {
            // given
            Long userId = 1L;
            CreateOrderRequest request = createRequest(List.of());
            User user = createTestUser(userId);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> orderService.createFromCart(userId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문할 상품이 없습니다");
        }
    }

    // ===== 주문 조회 테스트 =====

    @Nested
    @DisplayName("주문 조회 테스트")
    class GetOrderTest {

        @Test
        @DisplayName("주문 조회 성공")
        void getOrder_Success() {
            // given
            Long userId = 1L;
            Long orderId = 100L;
            User user = createTestUser(userId);
            Order order = createTestOrder(orderId, user, OrderStatus.PENDING);

            given(orderRepository.findByIdWithItems(orderId)).willReturn(Optional.of(order));

            // when
            OrderResponse response = orderService.getOrder(orderId, userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getOrderId()).isEqualTo(orderId);
            verify(orderRepository, times(1)).findByIdWithItems(orderId);
        }

        @Test
        @DisplayName("주문 조회 실패 - 주문 없음")
        void getOrder_Fail_OrderNotFound() {
            // given
            Long userId = 1L;
            Long orderId = 999L;

            given(orderRepository.findByIdWithItems(orderId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.getOrder(orderId, userId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("주문 조회 실패 - 권한 없음 (다른 사용자의 주문)")
        void getOrder_Fail_NotOwner() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            Long orderId = 100L;
            User otherUser = createTestUser(otherUserId);
            Order order = createTestOrder(orderId, otherUser, OrderStatus.PENDING);

            given(orderRepository.findByIdWithItems(orderId)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> orderService.getOrder(orderId, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인의 주문만 조회할 수 있습니다");
        }
    }

    // ===== 주문 목록 조회 테스트 =====

    @Nested
    @DisplayName("주문 목록 조회 테스트")
    class GetMyOrdersTest {

        @Test
        @DisplayName("주문 목록 조회 성공")
        void getMyOrders_Success() {
            // given
            Long userId = 1L;
            User user = createTestUser(userId);
            List<Order> orders = List.of(
                    createTestOrder(1L, user, OrderStatus.PENDING),
                    createTestOrder(2L, user, OrderStatus.PAID)
            );

            given(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)).willReturn(orders);

            // when
            List<OrderResponse> responses = orderService.getMyOrders(userId);

            // then
            assertThat(responses).hasSize(2);
            verify(orderRepository, times(1)).findByUserIdOrderByCreatedAtDesc(userId);
        }

        @Test
        @DisplayName("주문 목록 조회 성공 - 빈 목록")
        void getMyOrders_Success_EmptyList() {
            // given
            Long userId = 1L;

            given(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)).willReturn(List.of());

            // when
            List<OrderResponse> responses = orderService.getMyOrders(userId);

            // then
            assertThat(responses).isEmpty();
        }
    }

    // ===== 주문 취소 테스트 =====

    @Nested
    @DisplayName("주문 취소 테스트")
    class CancelOrderTest {

        @Test
        @DisplayName("주문 취소 성공")
        void cancelOrder_Success() {
            // given
            Long userId = 1L;
            Long orderId = 100L;
            User user = createTestUser(userId);
            Order order = createTestOrder(orderId, user, OrderStatus.PENDING);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            orderService.cancelOrder(orderId, userId);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("주문 취소 실패 - 이미 결제된 주문")
        void cancelOrder_Fail_AlreadyPaid() {
            // given
            Long userId = 1L;
            Long orderId = 100L;
            User user = createTestUser(userId);
            Order order = createTestOrder(orderId, user, OrderStatus.PAID);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> orderService.cancelOrder(orderId, userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("결제 대기 상태의 주문만 취소할 수 있습니다");
        }
    }
}
