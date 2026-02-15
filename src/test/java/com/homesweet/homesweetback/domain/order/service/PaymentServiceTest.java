package com.homesweet.homesweetback.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
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
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.order.entity.Payment;
import com.homesweet.homesweetback.domain.order.entity.PaymentStatus;
import com.homesweet.homesweetback.domain.order.repository.PaymentRepository;

/**
 * PaymentService 단위 테스트
 * 
 * 테스트 케이스:
 * 1. 결제 승인 - 성공
 * 2. 결제 승인 - 실패 (주문 없음)
 * 3. 결제 승인 - 실패 (금액 불일치)
 * 4. 결제 승인 - 실패 (권한 없음)
 * 5. 결제 취소 - 성공
 * 6. 결제 취소 - 실패 (권한 없음)
 * 7. 결제 조회 - 성공
 * 8. 결제 조회 - 실패 (결제 없음)
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
    private PaymentTransactionalService paymentTransactionalService;

    @Mock
    private PaymentRedisGuardService paymentRedisGuardService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

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
            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(paymentKey, orderNumber, amount);
            Map<String, Object> tossResponse = Map.of(
                    "paymentKey", paymentKey,
                    "orderId", orderNumber,
                    "status", "DONE",
                    "method", "카드",
                    "requestedAt", "2026-01-21T10:00:00+09:00",
                    "approvedAt", "2026-01-21T10:00:05+09:00"
            );
            PaymentResponse expected = PaymentResponse.builder().paymentId(1L).paymentKey(paymentKey).build();

            given(paymentRedisGuardService.tryAcquireIdempotency(paymentKey)).willReturn(true);
            given(paymentRedisGuardService.tryAcquireOrderLock(orderNumber)).willReturn("lock-token");
            given(tossPaymentsService.confirmPayment(request)).willReturn(tossResponse);
            given(paymentTransactionalService.persistConfirmedPayment(userId, request, tossResponse)).willReturn(expected);

            PaymentResponse response = paymentService.confirmPayment(userId, request);

            assertThat(response).isNotNull();
            verify(tossPaymentsService, times(1)).confirmPayment(request);
            verify(paymentTransactionalService, times(1)).persistConfirmedPayment(userId, request, tossResponse);
            verify(paymentRedisGuardService, times(1)).markIdempotencyCompleted(paymentKey);
            verify(paymentRedisGuardService, times(1)).releaseOrderLock(orderNumber, "lock-token");
        }

        @Test
        @DisplayName("결제 승인 중복 요청 - 기존 결제 재활용")
        void confirmPayment_Idempotent_ReturnExisting() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            Long amount = 100000L;
            String paymentKey = "test_payment_key_123";

            User user = createTestUser(userId);
            Order order = createTestOrder(1L, user, orderNumber, amount);
            Payment existingPayment = createTestPayment(10L, order, paymentKey);

            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(paymentKey, orderNumber, amount);

            given(paymentRedisGuardService.tryAcquireIdempotency(paymentKey)).willReturn(false);
            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(existingPayment));

            PaymentResponse response = paymentService.confirmPayment(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.getPaymentId()).isEqualTo(existingPayment.getId());
            verify(tossPaymentsService, never()).confirmPayment(request);
            verify(paymentTransactionalService, never()).persistConfirmedPayment(any(), any(), any());
        }

        @Test
        @DisplayName("결제 승인 실패 - 이미 처리 중인 요청")
        void confirmPayment_Fail_AlreadyProcessing() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            Long amount = 100000L;
            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest("test_payment_key_123", orderNumber, amount);

            given(paymentRedisGuardService.tryAcquireIdempotency(request.getPaymentKey())).willReturn(false);
            given(paymentRepository.findByPaymentKey(request.getPaymentKey())).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.confirmPayment(userId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 처리 중인 결제 요청입니다.");
        }

        @Test
        @DisplayName("결제 승인 실패 - 주문 락 획득 실패")
        void confirmPayment_Fail_OrderLockAcquire() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest("key", orderNumber, 100000L);

            given(paymentRedisGuardService.tryAcquireIdempotency("key")).willReturn(true);
            given(paymentRedisGuardService.tryAcquireOrderLock(orderNumber)).willReturn(null);

            assertThatThrownBy(() -> paymentService.confirmPayment(userId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 해당 주문의 결제가 처리 중입니다.");

            verify(paymentRedisGuardService, times(1)).clearIdempotency("key");
            verify(tossPaymentsService, never()).confirmPayment(any());
        }

        @Test
        @DisplayName("결제 승인 실패 - 금액 불일치")
        void confirmPayment_Fail_AmountMismatch() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest("key", orderNumber, 50000L);
            Map<String, Object> tossResponse = Map.of("status", "DONE");

            given(paymentRedisGuardService.tryAcquireIdempotency("key")).willReturn(true);
            given(paymentRedisGuardService.tryAcquireOrderLock(orderNumber)).willReturn("lock-token");
            given(tossPaymentsService.confirmPayment(request)).willReturn(tossResponse);
            given(paymentTransactionalService.persistConfirmedPayment(userId, request, tossResponse))
                    .willThrow(new PaymentMismatchException("결제 금액이 주문 금액과 일치하지 않습니다."));

            assertThatThrownBy(() -> paymentService.confirmPayment(userId, request))
                    .isInstanceOf(PaymentMismatchException.class)
                    .hasMessageContaining("결제 금액이 주문 금액과 일치하지 않습니다.");

            verify(tossPaymentsService, times(1)).cancelPayment(anyString(), any(TossPaymentCancelRequest.class));
            verify(paymentRedisGuardService, times(1)).clearIdempotency("key");
            verify(paymentRedisGuardService, times(1)).releaseOrderLock(orderNumber, "lock-token");
        }

        @Test
        @DisplayName("결제 승인 실패 - 주문 없음")
        void confirmPayment_Fail_OrderNotFound() {
            Long userId = 1L;
            String orderNumber = "TEST-ORDER-001";
            TossPaymentConfirmRequest request = new TossPaymentConfirmRequest("key", orderNumber, 100000L);
            Map<String, Object> tossResponse = Map.of("status", "DONE");

            given(paymentRedisGuardService.tryAcquireIdempotency("key")).willReturn(true);
            given(paymentRedisGuardService.tryAcquireOrderLock(orderNumber)).willReturn("lock-token");
            given(tossPaymentsService.confirmPayment(request)).willReturn(tossResponse);
            given(paymentTransactionalService.persistConfirmedPayment(userId, request, tossResponse))
                    .willThrow(new OrderNotFoundException("주문을 찾을 수 없습니다."));

            assertThatThrownBy(() -> paymentService.confirmPayment(userId, request))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(tossPaymentsService, times(1)).cancelPayment(anyString(), any(TossPaymentCancelRequest.class));
            verify(paymentRedisGuardService, times(1)).clearIdempotency("key");
            verify(paymentRedisGuardService, times(1)).releaseOrderLock(orderNumber, "lock-token");
        }
    }

    // ===== 결제 취소 테스트 =====

    @Nested
    @DisplayName("결제 취소 테스트")
    class CancelPaymentTest {

        @Test
        @DisplayName("결제 취소 성공")
        void cancelPayment_Success() {
            // given
            Long userId = 1L;
            String paymentKey = "test_payment_key_123";

            User user = createTestUser(userId);
            Order order = createTestOrder(1L, user, "TEST-ORDER-001", 100000L);
            Payment payment = createTestPayment(1L, order, paymentKey);

            TossPaymentCancelRequest request = new TossPaymentCancelRequest("고객 요청", null);

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(payment));

            // when
            PaymentResponse response = paymentService.cancelPayment(userId, paymentKey, request);

            // then
            assertThat(response).isNotNull();
            verify(tossPaymentsService, times(1)).cancelPayment(paymentKey, request);
            verify(paymentRepository, times(1)).save(payment);
        }

        @Test
        @DisplayName("결제 취소 실패 - 권한 없음")
        void cancelPayment_Fail_NotOwner() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            String paymentKey = "test_payment_key_123";

            User otherUser = createTestUser(otherUserId);
            Order order = mock(Order.class);
            given(order.isOwner(userId)).willReturn(false);

            Payment payment = Payment.builder()
                    .order(order)
                    .paymentKey(paymentKey)
                    .build();

            TossPaymentCancelRequest request = new TossPaymentCancelRequest("고객 요청", null);

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentService.cancelPayment(userId, paymentKey, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인의 결제만 취소할 수 있습니다");
        }
    }

    // ===== 결제 조회 테스트 =====

    @Nested
    @DisplayName("결제 조회 테스트")
    class GetPaymentTest {

        @Test
        @DisplayName("결제 조회 성공")
        void getPayment_Success() {
            // given
            Long userId = 1L;
            String paymentKey = "test_payment_key_123";

            User user = createTestUser(userId);
            Order order = createTestOrder(1L, user, "TEST-ORDER-001", 100000L);
            Payment payment = createTestPayment(1L, order, paymentKey);

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(payment));

            // when
            PaymentResponse response = paymentService.getPayment(userId, paymentKey);

            // then
            assertThat(response).isNotNull();
            verify(paymentRepository, times(1)).findByPaymentKey(paymentKey);
        }

        @Test
        @DisplayName("결제 조회 실패 - 결제 없음")
        void getPayment_Fail_NotFound() {
            // given
            Long userId = 1L;
            String paymentKey = "invalid_key";

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.getPayment(userId, paymentKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("결제 정보를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("결제 조회 실패 - 권한 없음")
        void getPayment_Fail_NotOwner() {
            // given
            Long userId = 1L;
            String paymentKey = "test_payment_key_123";

            Order order = mock(Order.class);
            given(order.isOwner(userId)).willReturn(false);

            Payment payment = Payment.builder()
                    .order(order)
                    .paymentKey(paymentKey)
                    .build();

            given(paymentRepository.findByPaymentKey(paymentKey)).willReturn(Optional.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentService.getPayment(userId, paymentKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인의 결제만 조회할 수 있습니다");
        }
    }
}
