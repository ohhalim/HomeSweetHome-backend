package com.homesweet.homesweetback.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.homesweet.homesweetback.common.config.TossPaymentsConfig;
import com.homesweet.homesweetback.common.exception.TossApiFailedException;
import com.homesweet.homesweetback.common.util.PaymentApiClient;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;

/**
 * TossPaymentsService 통합 테스트
 *
 * RestTemplate과 PaymentApiClient를 Mock하여 실제 HTTP 호출 없이
 * 서비스 계층의 통합 동작을 검증합니다.
 * - Authorization 헤더 생성 검증
 * - 요청 바디 구성 검증  
 * - 응답 처리 검증
 * - 에러 핸들링 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TossPaymentsService 통합 테스트")
class TossPaymentsServiceIntegrationTest {

    @Mock
    private TossPaymentsConfig tossPaymentsConfig;

    @Mock
    private PaymentApiClient paymentApiClient;

    @Mock
    private RestTemplate restTemplate;

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> httpEntityCaptor;

    @Captor
    private ArgumentCaptor<HttpEntity<Void>> httpEntityVoidCaptor;

    private TossPaymentsService tossPaymentsService;

    private static final String TEST_SECRET_KEY = "test_sk_integration_12345";
    private static final String TEST_PAYMENT_KEY = "tgen_20260205_integration_abc";
    private static final String TEST_ORDER_ID = "INTEG-ORDER-001";
    private static final Long TEST_AMOUNT = 150000L;

    private String expectedAuthHeader;

    @BeforeEach
    void setUp() {
        tossPaymentsService = new TossPaymentsService(tossPaymentsConfig, paymentApiClient, restTemplate);

        String credentials = TEST_SECRET_KEY + ":";
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        expectedAuthHeader = "Basic " + encodedCredentials;

        given(tossPaymentsConfig.getSecretKey()).willReturn(TEST_SECRET_KEY);
    }

    // ===== 결제 승인 통합 테스트 =====

    @Nested
    @DisplayName("결제 승인 통합 테스트")
    class ConfirmPaymentIntegrationTest {

        @Test
        @DisplayName("Authorization 헤더가 올바르게 생성되어 전송됨")
        void confirmPayment_AuthorizationHeaderCorrectlyGenerated() {
            // given
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey(TEST_PAYMENT_KEY)
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), httpEntityCaptor.capture()))
                    .willReturn(Map.of("status", "DONE"));

            // when
            tossPaymentsService.confirmPayment(request);

            // then
            HttpEntity<Map<String, Object>> capturedEntity = httpEntityCaptor.getValue();
            HttpHeaders headers = capturedEntity.getHeaders();

            assertThat(headers.getFirst("Authorization")).isEqualTo(expectedAuthHeader);
            assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        }

        @Test
        @DisplayName("요청 바디에 필수 파라미터가 모두 포함됨")
        void confirmPayment_RequestBodyContainsAllRequiredParams() {
            // given
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey(TEST_PAYMENT_KEY)
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), httpEntityCaptor.capture()))
                    .willReturn(Map.of("status", "DONE"));

            // when
            tossPaymentsService.confirmPayment(request);

            // then
            Map<String, Object> capturedBody = httpEntityCaptor.getValue().getBody();

            assertThat(capturedBody).containsEntry("paymentKey", TEST_PAYMENT_KEY);
            assertThat(capturedBody).containsEntry("orderId", TEST_ORDER_ID);
            assertThat(capturedBody).containsEntry("amount", TEST_AMOUNT);
        }

        @Test
        @DisplayName("토스 API 응답 데이터가 그대로 반환됨")
        void confirmPayment_ReturnsApiResponse() {
            // given
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey(TEST_PAYMENT_KEY)
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            Map<String, Object> apiResponse = Map.of(
                    "paymentKey", TEST_PAYMENT_KEY,
                    "orderId", TEST_ORDER_ID,
                    "status", "DONE",
                    "method", "카드",
                    "totalAmount", TEST_AMOUNT,
                    "requestedAt", "2026-02-05T17:30:00+09:00",
                    "approvedAt", "2026-02-05T17:30:05+09:00"
            );

            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(apiResponse);

            // when
            Map<String, Object> result = tossPaymentsService.confirmPayment(request);

            // then
            assertThat(result).containsEntry("paymentKey", TEST_PAYMENT_KEY);
            assertThat(result).containsEntry("status", "DONE");
            assertThat(result).containsEntry("method", "카드");
        }

        @Test
        @DisplayName("4xx 클라이언트 에러 발생 시 TossApiFailedException으로 변환")
        void confirmPayment_ClientError_ThrowsTossApiFailedException() {
            // given
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey("invalid_key")
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid Request"));

            // when & then
            assertThatThrownBy(() -> tossPaymentsService.confirmPayment(request))
                    .isInstanceOf(TossApiFailedException.class)
                    .hasMessageContaining("결제 승인 실패");
        }

        @Test
        @DisplayName("5xx 서버 에러 발생 시 TossApiFailedException으로 변환")
        void confirmPayment_ServerError_ThrowsTossApiFailedException() {
            // given
            TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                    .paymentKey(TEST_PAYMENT_KEY)
                    .orderId(TEST_ORDER_ID)
                    .amount(TEST_AMOUNT)
                    .build();

            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"));

            // when & then
            assertThatThrownBy(() -> tossPaymentsService.confirmPayment(request))
                    .isInstanceOf(TossApiFailedException.class);
        }
    }

    // ===== 결제 취소 통합 테스트 =====

    @Nested
    @DisplayName("결제 취소 통합 테스트")
    class CancelPaymentIntegrationTest {

        @Test
        @DisplayName("전체 취소 시 cancelAmount가 요청에 포함되지 않음")
        void cancelPayment_FullCancel_DoesNotIncludeCancelAmount() {
            // given
            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("고객 요청")
                    .build();

            given(tossPaymentsConfig.getCancelUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY + "/cancel");
            given(paymentApiClient.sendPostRequest(anyString(), httpEntityCaptor.capture()))
                    .willReturn(Map.of("status", "CANCELED"));

            // when
            tossPaymentsService.cancelPayment(TEST_PAYMENT_KEY, request);

            // then
            Map<String, Object> capturedBody = httpEntityCaptor.getValue().getBody();

            assertThat(capturedBody).containsEntry("cancelReason", "고객 요청");
            assertThat(capturedBody).doesNotContainKey("cancelAmount");
        }

        @Test
        @DisplayName("부분 취소 시 cancelAmount가 요청에 포함됨")
        void cancelPayment_PartialCancel_IncludesCancelAmount() {
            // given
            Long partialAmount = 50000L;
            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("부분 환불")
                    .cancelAmount(partialAmount)
                    .build();

            given(tossPaymentsConfig.getCancelUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY + "/cancel");
            given(paymentApiClient.sendPostRequest(anyString(), httpEntityCaptor.capture()))
                    .willReturn(Map.of("status", "PARTIAL_CANCELED"));

            // when
            tossPaymentsService.cancelPayment(TEST_PAYMENT_KEY, request);

            // then
            Map<String, Object> capturedBody = httpEntityCaptor.getValue().getBody();

            assertThat(capturedBody).containsEntry("cancelReason", "부분 환불");
            assertThat(capturedBody).containsEntry("cancelAmount", partialAmount);
        }

        @Test
        @DisplayName("취소 URL에 paymentKey가 올바르게 포함됨")
        void cancelPayment_UrlContainsPaymentKey() {
            // given
            TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                    .cancelReason("테스트")
                    .build();

            String expectedUrl = "https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY + "/cancel";
            given(tossPaymentsConfig.getCancelUrl(TEST_PAYMENT_KEY)).willReturn(expectedUrl);
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(Map.of("status", "CANCELED"));

            // when
            tossPaymentsService.cancelPayment(TEST_PAYMENT_KEY, request);

            // then
            verify(paymentApiClient).sendPostRequest(eq(expectedUrl), any(HttpEntity.class));
        }
    }

    // ===== 결제 조회 통합 테스트 =====

    @Nested
    @DisplayName("결제 조회 통합 테스트")
    class GetPaymentIntegrationTest {

        @Test
        @DisplayName("paymentKey 조회 시 올바른 URL로 GET 요청")
        void getPaymentByPaymentKey_CorrectUrl() {
            // given
            String expectedUrl = "https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY;
            given(tossPaymentsConfig.getPaymentUrl(TEST_PAYMENT_KEY)).willReturn(expectedUrl);
            given(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .willReturn(new ResponseEntity<>(Map.of("status", "DONE"), HttpStatus.OK));

            // when
            tossPaymentsService.getPaymentByPaymentKey(TEST_PAYMENT_KEY);

            // then
            verify(restTemplate).exchange(eq(expectedUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
        }

        @Test
        @DisplayName("orderId 조회 시 올바른 URL로 GET 요청")
        void getPaymentByOrderId_CorrectUrl() {
            // given
            String expectedUrl = "https://api.tosspayments.com/v1/payments/orders/" + TEST_ORDER_ID;
            given(tossPaymentsConfig.getOrderIdUrl(TEST_ORDER_ID)).willReturn(expectedUrl);
            given(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(Map.class)))
                    .willReturn(new ResponseEntity<>(Map.of("status", "DONE"), HttpStatus.OK));

            // when
            tossPaymentsService.getPaymentByOrderId(TEST_ORDER_ID);

            // then
            verify(restTemplate).exchange(eq(expectedUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
        }

        @Test
        @DisplayName("조회 시 Authorization 헤더가 포함됨")
        void getPayment_IncludesAuthorizationHeader() {
            // given
            given(tossPaymentsConfig.getPaymentUrl(TEST_PAYMENT_KEY))
                    .willReturn("https://api.tosspayments.com/v1/payments/" + TEST_PAYMENT_KEY);
            given(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    httpEntityVoidCaptor.capture(),
                    eq(Map.class)))
                    .willReturn(new ResponseEntity<>(Map.of("status", "DONE"), HttpStatus.OK));

            // when
            tossPaymentsService.getPaymentByPaymentKey(TEST_PAYMENT_KEY);

            // then
            HttpHeaders headers = httpEntityVoidCaptor.getValue().getHeaders();
            assertThat(headers.getFirst("Authorization")).isEqualTo(expectedAuthHeader);
        }
    }
}
