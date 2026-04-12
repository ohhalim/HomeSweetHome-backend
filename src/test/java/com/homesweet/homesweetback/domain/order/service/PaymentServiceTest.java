package com.homesweet.homesweetback.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.homesweet.homesweetback.common.exception.OrderNotFoundException;
import com.homesweet.homesweetback.common.exception.PaymentMismatchException;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.entity.UserRole;
import com.homesweet.homesweetback.domain.order.dto.PaymentResponse;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.Payment;
import com.homesweet.homesweetback.domain.order.entity.PaymentStatus;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.order.repository.PaymentRepository;
import com.homesweet.homesweetback.domain.product.cart.repository.jpa.CartJPARepository;

/**
 * PaymentService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService 테스트")
class PaymentServiceTest {

    @Mock
    private TossPaymentsService tossPaymentsService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartJPARepository cartJPARepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    // ===== 테스트 데이터 생성 헬퍼 =====

    private User createTestUser(Long userId) {
        User user = User.builder()
                .email("test@test.com")
                .name("테스트유저")
                .role(UserRole.USER)
                .build();
        user.setId(userId);
        return user;
    }

    private Order createTestOrder(Long orderId, User user, String orderNumber, Long amount) {
        Order order = mock(Order.class);
        given(order.getId()).willReturn(orderId);
        given(order.getUser()).willReturn(user);
        given(order.getOrderNumber()).willReturn(orderNumber);
        given(order.getTotalAmount()).willReturn(amount);
        given(order.isOwner(user.getId())).willReturn(true);
        given(order.isPending()).willReturn(true);
        return order;
    }

    private Payment createTestPayment(Long paymentId, Order order, String paymentKey) {
        return Payment.builder()
                .id(paymentId)
                .order(order)
                .paymentKey(paymentKey)
                .tossOrderId(order.getOrderNumber())
                .status(PaymentStatus.DONE)
                .amount(order.getTotalAmount())
                .build();
    }

    private Payment createTestPaymentWithStatus(Long paymentId, Order order, String paymentKey, PaymentStatus status) {
        return Payment.builder()
                .id(paymentId)
                .order(order)
                .paymentKey(paymentKey)
                .tossOrderId(order.getOrderNumber())
                .status(status)
                .amount(order.getTotalAmount())
                .build();
    }

    // ===== 결제 승인 테스트 =====

    @Nested
    @DisplayName("결제 승인 테스트")
    class ConfirmPaymentTest {

        @Test
        @DisplayName("결제 승인 성공")
        void confirmPayment_Success() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            Long amount = 100000L;
            String paymentKey = "test_payment_key_123";
            User user = createTestUser(userId);
            Order order = createTestOrder(1L, user, orderNumber, amount);

            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(paymentKey, orderNumber, amount);
            Map<String, Object> tossResponse = Map.of(
                    "paymentKey", paymentKey,
                    "orderId", orderNumber,
                    "status", "DONE",
                    "method", "카드",
                    "requestedAt", "2026-01-21T10:00:00+09:00",
                    "approvedAt", "2026-01-21T10:00:05+09:00");

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.empty());
            given(orderRepository.findByOrderNumberWithItemsForUpdate(orderNumber)).willReturn(Optional.of(order));
            given(tossPaymentsService.confirmPayment(request)).willReturn(tossResponse);

            PaymentResponse response = paymentService.confirmPayment(userId, request);

            assertThat(response).isNotNull();
            verify(tossPaymentsService, times(1)).confirmPayment(request);
            verify(paymentRepository, times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("결제 승인 중복 요청 - 기존 결제 반환")
        void confirmPayment_Idempotent_ReturnExisting() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            Long amount = 100000L;
            String paymentKey = "test_payment_key_123";

            User user = createTestUser(userId);
            Order order = createTestOrder(1L, user, orderNumber, amount);
            Payment existingPayment = createTestPayment(10L, order, paymentKey);

            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(paymentKey, orderNumber, amount);

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(existingPayment));

            PaymentResponse response = paymentService.confirmPayment(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.getPaymentId()).isEqualTo(existingPayment.getId());
            verify(tossPaymentsService, never()).confirmPayment(any());
        }

        @Test
        @DisplayName("결제 승인 중복 요청 실패 - 기존 결제 권한 없음")
        void confirmPayment_Idempotent_Fail_NotOwner() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            Long amount = 100000L;
            String paymentKey = "test_payment_key_123";

            Order order = mock(Order.class);
            given(order.getOrderNumber()).willReturn(orderNumber);
            given(order.getTotalAmount()).willReturn(amount);
            given(order.isOwner(userId)).willReturn(false);

            Payment existingPayment = createTestPayment(10L, order, paymentKey);
            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(paymentKey, orderNumber, amount);

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(existingPayment));

            assertThatThrownBy(() -> paymentService.confirmPayment(userId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인의 주문만 결제할 수 있습니다.");
            verify(tossPaymentsService, never()).confirmPayment(any());
        }

        @Test
        @DisplayName("결제 승인 실패 - 주문 없음")
        void confirmPayment_Fail_OrderNotFound() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest("key", orderNumber, 100000L);

            given(paymentRepository.findByPaymentKey("key")).willReturn(Optional.empty());
            given(orderRepository.findByOrderNumberWithItemsForUpdate(orderNumber)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.confirmPayment(userId, request))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("결제 승인 실패 - 금액 불일치")
        void confirmPayment_Fail_AmountMismatch() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            User user = createTestUser(userId);
            Order order = createTestOrder(1L, user, orderNumber, 100000L);

            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest("key", orderNumber, 50000L);

            given(paymentRepository.findByPaymentKey("key")).willReturn(Optional.empty());
            given(orderRepository.findByOrderNumberWithItemsForUpdate(orderNumber)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.confirmPayment(userId, request))
                    .isInstanceOf(PaymentMismatchException.class)
                    .hasMessageContaining("결제 금액이 주문 금액과 일치하지 않습니다.");
        }
    }

    // ===== 결제 조회 테스트 =====

    @Nested
    @DisplayName("결제 조회 테스트")
    class GetPaymentTest {

        @Test
        @DisplayName("결제 조회 성공")
        void getPayment_Success() {
            Long userId = 1L;
            String paymentKey = "test_payment_key_123";

            User user = createTestUser(userId);
            Order order = createTestOrder(1L, user, "TEST-ORDER-001", 100000L);
            Payment payment = createTestPayment(1L, order, paymentKey);

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(payment));

            PaymentResponse response = paymentService.getPayment(userId, paymentKey);

            assertThat(response).isNotNull();
            verify(paymentRepository, times(1)).findByPaymentKey(paymentKey);
        }

        @Test
        @DisplayName("결제 조회 실패 - 결제 없음")
        void getPayment_Fail_NotFound() {
            Long userId = 1L;
            String paymentKey = "invalid_key";

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPayment(userId, paymentKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("결제 정보를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("결제 조회 실패 - 권한 없음")
        void getPayment_Fail_NotOwner() {
            Long userId = 1L;
            String paymentKey = "test_payment_key_123";

            Order order = mock(Order.class);
            given(order.isOwner(userId)).willReturn(false);

            Payment payment = Payment.builder()
                    .order(order)
                    .paymentKey(paymentKey)
                    .build();

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.getPayment(userId, paymentKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인의 결제만 조회할 수 있습니다");
        }
    }

    // ===== 결제 취소 테스트 =====

    @Nested
    @DisplayName("결제 취소 테스트")
    class CancelPaymentTest {

        @Test
        @DisplayName("결제 전체 취소 성공")
        void cancelPayment_FullCancel_Success() {
            Long userId = 1L;
            String paymentKey = "test_payment_key_123";

            User user = createTestUser(userId);
            Order order = createTestOrder(1L, user, "TEST-ORDER-001", 100000L);
            Payment payment = createTestPaymentWithStatus(10L, order, paymentKey, PaymentStatus.DONE);

            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("고객 요청")
                    .build();

            given(paymentRepository.findByPaymentKeyWithOrderItemsForUpdate(paymentKey))
                    .willReturn(Optional.of(payment));
            given(tossPaymentsService.cancelPayment(paymentKey, request))
                    .willReturn(Map.of("paymentKey", paymentKey, "status", "CANCELED"));

            PaymentResponse response = paymentService.cancelPayment(userId, paymentKey, request);

            assertThat(response).isNotNull();
            assertThat(response.getPaymentId()).isEqualTo(payment.getId());
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verify(order).cancel();
            verify(tossPaymentsService, times(1)).cancelPayment(paymentKey, request);
        }

        @Test
        @DisplayName("결제 부분 취소 성공")
        void cancelPayment_PartialCancel_Success() {
            Long userId = 1L;
            String paymentKey = "test_payment_key_123";

            User user = createTestUser(userId);
            Order order = createTestOrder(1L, user, "TEST-ORDER-001", 100000L);
            Payment payment = createTestPaymentWithStatus(11L, order, paymentKey, PaymentStatus.DONE);

            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("부분 환불")
                    .cancelAmount(30000L)
                    .build();

            given(paymentRepository.findByPaymentKeyWithOrderItemsForUpdate(paymentKey))
                    .willReturn(Optional.of(payment));
            given(tossPaymentsService.cancelPayment(paymentKey, request))
                    .willReturn(Map.of("paymentKey", paymentKey, "status", "PARTIAL_CANCELED"));

            PaymentResponse response = paymentService.cancelPayment(userId, paymentKey, request);

            assertThat(response).isNotNull();
            assertThat(response.getPaymentId()).isEqualTo(payment.getId());
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
            verify(order, never()).cancel();
            verify(tossPaymentsService, times(1)).cancelPayment(paymentKey, request);
        }

        @Test
        @DisplayName("결제 취소 실패 - 결제 없음")
        void cancelPayment_Fail_NotFound() {
            Long userId = 1L;
            String paymentKey = "missing_payment_key";

            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("고객 요청")
                    .build();

            given(paymentRepository.findByPaymentKeyWithOrderItemsForUpdate(paymentKey))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.cancelPayment(userId, paymentKey, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("결제 정보를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("결제 취소 실패 - 권한 없음")
        void cancelPayment_Fail_NotOwner() {
            Long userId = 1L;
            String paymentKey = "test_payment_key_123";

            User owner = createTestUser(2L);
            Order order = createTestOrder(1L, owner, "TEST-ORDER-001", 100000L);
            given(order.isOwner(userId)).willReturn(false);

            Payment payment = createTestPaymentWithStatus(12L, order, paymentKey, PaymentStatus.DONE);

            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("고객 요청")
                    .build();

            given(paymentRepository.findByPaymentKeyWithOrderItemsForUpdate(paymentKey))
                    .willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.cancelPayment(userId, paymentKey, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인의 결제만 취소할 수 있습니다.");
            verify(tossPaymentsService, never()).cancelPayment(any(), any());
        }
    }
}
