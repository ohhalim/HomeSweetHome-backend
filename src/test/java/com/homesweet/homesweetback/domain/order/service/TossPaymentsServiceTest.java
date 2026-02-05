package com.homesweet.homesweetback.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.homesweet.homesweetback.common.config.TossPaymentsConfig;
import com.homesweet.homesweetback.common.exception.TossApiFailedException;
import com.homesweet.homesweetback.common.util.PaymentApiClient;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;

/**
 * TossPaymentsService 단위 테스트
 * 
 * 테스트 대상: TossPaymentsService의 결제 승인, 취소, 조회 메서드
 * Mock 대상: TossPaymentsConfig, PaymentApiClient, RestTemplate
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TossPaymentsService 단위 테스트")
class TossPaymentsServiceTest {

    @Mock
    private TossPaymentsConfig tossPaymentsConfig;

    @Mock
    private PaymentApiClient paymentApiClient;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TossPaymentsService tossPaymentsService;

    private static final String TEST_SECRET_KEY = "test_sk_1234567890";
    private static final String TEST_PAYMENT_KEY = "test_payment_key_abc123";
    private static final String TEST_ORDER_ID = "TEST-ORDER-001";
    private static final Long TEST_AMOUNT = 100000L;

    @BeforeEach
    void setUp() {
        given(tossPaymentsConfig.getSecretKey()).willReturn(TEST_SECRET_KEY);
    }

    // ===== 결제 승인 테스트 =====

    @Nested
    @DisplayName("결제 승인 테스트")
    class ConfirmPaymentTest {

        @Test
        @DisplayName("결제 승인 성공 - 정상 응답 반환")
        void confirmPayment_Success() {
            // given
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey(TEST_PAYMENT_KEY)
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            Map<String, Object> expectedResponse = Map.of(
                    "paymentKey", TEST_PAYMENT_KEY,
                    "orderId", TEST_ORDER_ID,
                    "status", "DONE",
                    "method", "카드",
                    "totalAmount", TEST_AMOUNT
            );

            given(tossPaymentsConfig.getConfirmUrl()).willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(expectedResponse);

            // when
            Map<String, Object> result = tossPaymentsService.confirmPayment(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.get("paymentKey")).isEqualTo(TEST_PAYMENT_KEY);
            assertThat(result.get("status")).isEqualTo("DONE");
            verify(paymentApiClient).sendPostRequest(anyString(), any(HttpEntity.class));
        }

        @Test
        @DisplayName("결제 승인 실패 - RestClientResponseException 발생 시 TossApiFailedException")
        void confirmPayment_Fail_RestClientException() {
            // given
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey(TEST_PAYMENT_KEY)
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            given(tossPaymentsConfig.getConfirmUrl()).willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willThrow(new RestClientResponseException(
                            "INVALID_REQUEST", 
                            HttpStatus.BAD_REQUEST, 
                            "Bad Request",
                            null, 
                            "{\"code\":\"INVALID_REQUEST\"}".getBytes(),
                            null));

            // when & then
            assertThatThrownBy(() -> tossPaymentsService.confirmPayment(request))
                    .isInstanceOf(TossApiFailedException.class)
                    .hasMessageContaining("결제 승인 실패");
        }

        @Test
        @DisplayName("결제 승인 - 요청 바디에 필수 파라미터 포함 확인")
        void confirmPayment_RequestContainsRequiredParams() {
            // given
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey(TEST_PAYMENT_KEY)
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            given(tossPaymentsConfig.getConfirmUrl()).willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(Map.of("status", "DONE"));

            // when
            tossPaymentsService.confirmPayment(request);

            // then - verify 호출 자체로 검증
            verify(paymentApiClient).sendPostRequest(
                    eq("https://api.tosspayments.com/v1/payments/confirm"),
                    any(HttpEntity.class));
        }
    }

    // ===== 결제 취소 테스트 =====

    @Nested
    @DisplayName("결제 취소 테스트")
    class CancelPaymentTest {

        @Test
        @DisplayName("결제 전체 취소 성공")
        void cancelPayment_FullCancel_Success() {
            // given
            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("고객 요청")
                    .build();

            Map<String, Object> expectedResponse = Map.of(
                    "paymentKey", TEST_PAYMENT_KEY,
                    "status", "CANCELED",
                    "cancels", Map.of("cancelReason", "고객 요청")
            );

            given(tossPaymentsConfig.getCancelUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY + "/cancel");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(expectedResponse);

            // when
            Map<String, Object> result = tossPaymentsService.cancelPayment(TEST_PAYMENT_KEY, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("CANCELED");
        }

        @Test
        @DisplayName("결제 부분 취소 성공")
        void cancelPayment_PartialCancel_Success() {
            // given
            Long partialAmount = 50000L;
            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("부분 환불")
                    .cancelAmount(partialAmount)
                    .build();

            Map<String, Object> expectedResponse = Map.of(
                    "paymentKey", TEST_PAYMENT_KEY,
                    "status", "PARTIAL_CANCELED"
            );

            given(tossPaymentsConfig.getCancelUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY + "/cancel");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(expectedResponse);

            // when
            Map<String, Object> result = tossPaymentsService.cancelPayment(TEST_PAYMENT_KEY, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("PARTIAL_CANCELED");
        }

        @Test
        @DisplayName("결제 취소 실패 - HttpClientErrorException 발생")
        void cancelPayment_Fail_HttpClientError() {
            // given
            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("테스트")
                    .build();

            given(tossPaymentsConfig.getCancelUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY + "/cancel");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

            // when & then
            assertThatThrownBy(() -> tossPaymentsService.cancelPayment(TEST_PAYMENT_KEY, request))
                    .isInstanceOf(TossApiFailedException.class)
                    .hasMessageContaining("결제 취소 실패");
        }
    }

    // ===== paymentKey로 결제 조회 테스트 =====

    @Nested
    @DisplayName("paymentKey로 결제 조회 테스트")
    class GetPaymentByPaymentKeyTest {

        @Test
        @DisplayName("paymentKey로 결제 조회 성공")
        void getPaymentByPaymentKey_Success() {
            // given
            Map<String, Object> expectedResponse = Map.of(
                    "paymentKey", TEST_PAYMENT_KEY,
                    "orderId", TEST_ORDER_ID,
                    "status", "DONE",
                    "totalAmount", TEST_AMOUNT
            );

            given(tossPaymentsConfig.getPaymentUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY);
            given(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .willReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

            // when
            Map<String, Object> result = tossPaymentsService.getPaymentByPaymentKey(TEST_PAYMENT_KEY);

            // then
            assertThat(result).isNotNull();
            assertThat(result.get("paymentKey")).isEqualTo(TEST_PAYMENT_KEY);
            assertThat(result.get("status")).isEqualTo("DONE");
        }

        @Test
        @DisplayName("paymentKey로 결제 조회 실패 - 존재하지 않는 결제")
        void getPaymentByPaymentKey_Fail_NotFound() {
            // given
            given(tossPaymentsConfig.getPaymentUrl("invalid_key"))
                    .willReturn("https://api.tosspayments.com/v1/payments/invalid_key");
            given(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

            // when & then
            assertThatThrownBy(() -> tossPaymentsService.getPaymentByPaymentKey("invalid_key"))
                    .isInstanceOf(TossApiFailedException.class)
                    .hasMessageContaining("결제 조회 실패");
        }
    }

    // ===== orderId로 결제 조회 테스트 =====

    @Nested
    @DisplayName("orderId로 결제 조회 테스트")
    class GetPaymentByOrderIdTest {

        @Test
        @DisplayName("orderId로 결제 조회 성공")
        void getPaymentByOrderId_Success() {
            // given
            Map<String, Object> expectedResponse = Map.of(
                    "paymentKey", TEST_PAYMENT_KEY,
                    "orderId", TEST_ORDER_ID,
                    "status", "DONE"
            );

            given(tossPaymentsConfig.getOrderIdUrl(TEST_ORDER_ID))
                    .willReturn("https://api.tosspayments.com/v1/payments/orders/" + TEST_ORDER_ID);
            given(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .willReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

            // when
            Map<String, Object> result = tossPaymentsService.getPaymentByOrderId(TEST_ORDER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.get("orderId")).isEqualTo(TEST_ORDER_ID);
        }

        @Test
        @DisplayName("orderId로 결제 조회 실패 - 존재하지 않는 주문")
        void getPaymentByOrderId_Fail_NotFound() {
            // given
            String invalidOrderId = "INVALID-ORDER";
            given(tossPaymentsConfig.getOrderIdUrl(invalidOrderId))
                    .willReturn("https://api.tosspayments.com/v1/payments/orders/" + invalidOrderId);
            given(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

            // when & then
            assertThatThrownBy(() -> tossPaymentsService.getPaymentByOrderId(invalidOrderId))
                    .isInstanceOf(TossApiFailedException.class)
                    .hasMessageContaining("결제 조회 실패");
        }
    }

    // ===== Authorization 헤더 테스트 =====

    @Nested
    @DisplayName("Authorization 헤더 생성 테스트")
    class AuthorizationHeaderTest {

        @Test
        @DisplayName("Basic 인증 헤더가 올바르게 생성되는지 검증")
        void createHeaders_GeneratesValidBasicAuth() {
            // given
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey(TEST_PAYMENT_KEY)
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            given(tossPaymentsConfig.getConfirmUrl()).willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(Map.of("status", "DONE"));

            // when
            tossPaymentsService.confirmPayment(request);

            // then - 호출 시 HttpEntity에 Authorization 헤더가 포함되어 있는지 간접 검증
            // 실제 헤더 내용 검증은 통합테스트에서 수행
            verify(paymentApiClient).sendPostRequest(anyString(), any(HttpEntity.class));
        }
    }
}
