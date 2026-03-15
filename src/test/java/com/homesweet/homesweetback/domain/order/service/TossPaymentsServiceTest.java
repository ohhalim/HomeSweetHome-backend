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
import com.homesweet.homesweetback.common.exception.TossApiClientException;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;

/**
 * TossPaymentsService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TossPaymentsService 단위 테스트")
class TossPaymentsServiceTest {

    @Mock
    private TossPaymentsConfig tossPaymentsConfig;

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

    @Nested
    @DisplayName("결제 승인 테스트")
    class ConfirmPaymentTest {

        @Test
        @DisplayName("결제 승인 성공 - 정상 응답 반환")
        void confirmPayment_Success() {
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
                    "totalAmount", TEST_AMOUNT);

            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .willReturn(expectedResponse);

            Map<String, Object> result = tossPaymentsService.confirmPayment(request);

            assertThat(result).isNotNull();
            assertThat(result.get("paymentKey")).isEqualTo(TEST_PAYMENT_KEY);
            assertThat(result.get("status")).isEqualTo("DONE");
        }

        @Test
        @DisplayName("결제 승인 실패 - RestClientResponseException 발생 시 TossApiClientException")
        void confirmPayment_Fail_RestClientException() {
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey(TEST_PAYMENT_KEY)
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .willThrow(new RestClientResponseException(
                            "INVALID_REQUEST",
                            HttpStatus.BAD_REQUEST,
                            "Bad Request",
                            null,
                            "{\"code\":\"INVALID_REQUEST\"}".getBytes(),
                            null));

            assertThatThrownBy(() -> tossPaymentsService.confirmPayment(request))
                    .isInstanceOf(TossApiClientException.class)
                    .hasMessageContaining("결제 승인 요청 오류");
        }
    }

    @Nested
    @DisplayName("결제 취소 테스트")
    class CancelPaymentTest {

        @Test
        @DisplayName("결제 전체 취소 성공")
        void cancelPayment_FullCancel_Success() {
            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("고객 요청")
                    .build();

            Map<String, Object> expectedResponse = Map.of(
                    "paymentKey", TEST_PAYMENT_KEY,
                    "status", "CANCELED");

            given(tossPaymentsConfig.getCancelUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY + "/cancel");
            given(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .willReturn(expectedResponse);

            Map<String, Object> result = tossPaymentsService.cancelPayment(TEST_PAYMENT_KEY, request);

            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("CANCELED");
        }

        @Test
        @DisplayName("결제 취소 실패 - HttpClientErrorException 발생")
        void cancelPayment_Fail_HttpClientError() {
            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("테스트")
                    .build();

            given(tossPaymentsConfig.getCancelUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY + "/cancel");
            given(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

            assertThatThrownBy(() -> tossPaymentsService.cancelPayment(TEST_PAYMENT_KEY, request))
                    .isInstanceOf(TossApiClientException.class)
                    .hasMessageContaining("결제 취소 요청 오류");
        }
    }

    @Nested
    @DisplayName("paymentKey로 결제 조회 테스트")
    class GetPaymentByPaymentKeyTest {

        @Test
        @DisplayName("paymentKey로 결제 조회 성공")
        void getPaymentByPaymentKey_Success() {
            Map<String, Object> expectedResponse = Map.of(
                    "paymentKey", TEST_PAYMENT_KEY,
                    "orderId", TEST_ORDER_ID,
                    "status", "DONE",
                    "totalAmount", TEST_AMOUNT);

            given(tossPaymentsConfig.getPaymentUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY);
            given(restTemplate.exchange(
                    anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .willReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

            Map<String, Object> result = tossPaymentsService.getPaymentByPaymentKey(TEST_PAYMENT_KEY);

            assertThat(result).isNotNull();
            assertThat(result.get("paymentKey")).isEqualTo(TEST_PAYMENT_KEY);
            assertThat(result.get("status")).isEqualTo("DONE");
        }

        @Test
        @DisplayName("paymentKey로 결제 조회 실패 - 존재하지 않는 결제")
        void getPaymentByPaymentKey_Fail_NotFound() {
            given(tossPaymentsConfig.getPaymentUrl("invalid_key"))
                    .willReturn("https://api.tosspayments.com/v1/payments/invalid_key");
            given(restTemplate.exchange(
                    anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

            assertThatThrownBy(() -> tossPaymentsService.getPaymentByPaymentKey("invalid_key"))
                    .isInstanceOf(TossApiClientException.class)
                    .hasMessageContaining("결제 조회 요청 오류");
        }
    }

    @Nested
    @DisplayName("orderId로 결제 조회 테스트")
    class GetPaymentByOrderIdTest {

        @Test
        @DisplayName("orderId로 결제 조회 성공")
        void getPaymentByOrderId_Success() {
            Map<String, Object> expectedResponse = Map.of(
                    "paymentKey", TEST_PAYMENT_KEY,
                    "orderId", TEST_ORDER_ID,
                    "status", "DONE");

            given(tossPaymentsConfig.getOrderIdUrl(TEST_ORDER_ID))
                    .willReturn("https://api.tosspayments.com/v1/payments/orders/" + TEST_ORDER_ID);
            given(restTemplate.exchange(
                    anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .willReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

            Map<String, Object> result = tossPaymentsService.getPaymentByOrderId(TEST_ORDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.get("orderId")).isEqualTo(TEST_ORDER_ID);
        }
    }
}
