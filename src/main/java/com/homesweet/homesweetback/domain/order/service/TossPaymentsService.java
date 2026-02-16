package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.common.config.TossPaymentsConfig;
import com.homesweet.homesweetback.common.exception.TossApiAuthenticationException;
import com.homesweet.homesweetback.common.exception.TossApiClientException;
import com.homesweet.homesweetback.common.exception.TossApiFailedException;
import com.homesweet.homesweetback.common.util.PaymentApiClient;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 토스페이먼츠 API 연동 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossPaymentsService {

    private static final Set<String> INVALID_SECRET_KEYS = Set.of("test_sk_dummy_key", "test_sk_XXXX");

    private final TossPaymentsConfig tossPaymentsConfig;
    private final PaymentApiClient paymentApiClient;
    private final RestTemplate restTemplate;

    /**
     * 결제 승인 요청
     * 프론트엔드에서 결제창 완료 후 받은 paymentKey, orderId, amount로 최종 승인
     */
    @CircuitBreaker(name = "toss-payments", fallbackMethod = "confirmFallback")
    public Map<String, Object> confirmPayment(TossPaymentConfirmRequest request) {
        log.info("결제 승인 요청: orderId={}, amount={}", request.getOrderId(), request.getAmount());

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", request.getPaymentKey());
        body.put("orderId", request.getOrderId());
        body.put("amount", request.getAmount());

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, createHeaders());

        try {
            Map<String, Object> response = paymentApiClient.sendPostRequest(
                    tossPaymentsConfig.getConfirmUrl(),
                    httpEntity);
            log.info("결제 승인 성공: paymentKey={}", request.getPaymentKey());
            return response;
        } catch (RestClientResponseException e) {
            throw classifyTossException("결제 승인", e);
        }
    }

    /**
     * 결제 취소 요청
     */
    @CircuitBreaker(name = "toss-payments", fallbackMethod = "cancelFallback")
    public Map<String, Object> cancelPayment(String paymentKey, TossPaymentCancelRequest request) {
        log.info("결제 취소 요청: paymentKey={}, reason={}", paymentKey, request.getCancelReason());

        Map<String, Object> body = new HashMap<>();
        body.put("cancelReason", request.getCancelReason());
        if (request.getCancelAmount() != null) {
            body.put("cancelAmount", request.getCancelAmount());
        }

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, createHeaders());

        try {
            Map<String, Object> response = paymentApiClient.sendPostRequest(
                    tossPaymentsConfig.getCancelUrl(paymentKey),
                    httpEntity);
            log.info("결제 취소 성공: paymentKey={}", paymentKey);
            return response;
        } catch (RestClientResponseException e) {
            throw classifyTossException("결제 취소", e);
        }
    }

    /**
     * paymentKey로 결제 조회
     */
    @CircuitBreaker(name = "toss-payments", fallbackMethod = "queryFallback")
    public Map<String, Object> getPaymentByPaymentKey(String paymentKey) {
        log.info("결제 조회 요청: paymentKey={}", paymentKey);

        HttpEntity<Void> httpEntity = new HttpEntity<>(createHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tossPaymentsConfig.getPaymentUrl(paymentKey),
                    HttpMethod.GET,
                    httpEntity,
                    Map.class);
            log.info("결제 조회 성공: paymentKey={}", paymentKey);
            return response.getBody();
        } catch (RestClientResponseException e) {
            throw classifyTossException("결제 조회", e);
        }
    }

    /**
     * orderId로 결제 조회
     */
    @CircuitBreaker(name = "toss-payments", fallbackMethod = "queryFallback")
    public Map<String, Object> getPaymentByOrderId(String orderId) {
        log.info("주문ID로 결제 조회 요청: orderId={}", orderId);

        HttpEntity<Void> httpEntity = new HttpEntity<>(createHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tossPaymentsConfig.getOrderIdUrl(orderId),
                    HttpMethod.GET,
                    httpEntity,
                    Map.class);
            log.info("주문ID로 결제 조회 성공: orderId={}", orderId);
            return response.getBody();
        } catch (RestClientResponseException e) {
            throw classifyTossException("결제 조회", e);
        }
    }

    /**
     * Basic 인증 헤더 생성
     * 토스페이먼츠는 시크릿키 + ":" 를 Base64 인코딩해서 Authorization 헤더에 전달
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String credentials = requireSecretKey() + ":";
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCredentials);

        return headers;
    }

    private String requireSecretKey() {
        String secretKey = tossPaymentsConfig.getSecretKey();
        if (!StringUtils.hasText(secretKey)) {
            throw new TossApiAuthenticationException(
                    "PG 인증 키가 비어 있습니다. `payments.toss.secretKey` (TOSS_PAYMENTS_SECRET_KEY/TOSS_SECRET_KEY)를 확인해주세요.");
        }

        String normalizedSecretKey = secretKey.trim();
        if (INVALID_SECRET_KEYS.contains(normalizedSecretKey)) {
            throw new TossApiAuthenticationException(
                    "PG 인증 키가 placeholder 값입니다. 실제 Toss 테스트/라이브 시크릿 키로 교체해주세요.");
        }
        return normalizedSecretKey;
    }

    private RuntimeException classifyTossException(String action, RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();

        if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            log.error("{} 인증 실패: status={}, secretKeySuffix={}, body={}",
                    action,
                    exception.getStatusCode(),
                    getSecretKeySuffix(),
                    responseBody);
            return new TossApiAuthenticationException(
                    "PG 인증 실패(401). Toss 시크릿키/클라이언트키 모드(test/live)와 paymentKey를 확인해주세요.",
                    exception);
        }

        if (exception.getStatusCode().is4xxClientError()) {
            log.warn("{} 요청 오류: status={}, body={}", action, exception.getStatusCode(), responseBody);
            return new TossApiClientException(action + " 요청 오류: " + responseBody, exception);
        }

        log.error("{} 연동 실패: status={}, body={}", action, exception.getStatusCode(), responseBody);
        return new TossApiFailedException(action + " 실패: " + responseBody, exception);
    }

    private String getSecretKeySuffix() {
        String secretKey = tossPaymentsConfig.getSecretKey();
        if (!StringUtils.hasText(secretKey)) {
            return "empty";
        }

        String normalizedSecretKey = secretKey.trim();
        if (normalizedSecretKey.length() <= 4) {
            return "***" + normalizedSecretKey;
        }
        return "***" + normalizedSecretKey.substring(normalizedSecretKey.length() - 4);
    }

    // ===== Fallback 메서드 =====

    private Map<String, Object> confirmFallback(TossPaymentConfirmRequest request, Throwable t) {
        log.error("결제 승인 서킷브레이커 작동: orderId={}, error={}", request.getOrderId(), t.getMessage());
        throw new TossApiFailedException("결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", t);
    }

    private Map<String, Object> cancelFallback(String paymentKey, TossPaymentCancelRequest request, Throwable t) {
        log.error("결제 취소 서킷브레이커 작동: paymentKey={}, error={}", paymentKey, t.getMessage());
        throw new TossApiFailedException("결제 취소 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", t);
    }

    private Map<String, Object> queryFallback(String key, Throwable t) {
        log.error("결제 조회 서킷브레이커 작동: key={}, error={}", key, t.getMessage());
        throw new TossApiFailedException("결제 조회 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", t);
    }
}
