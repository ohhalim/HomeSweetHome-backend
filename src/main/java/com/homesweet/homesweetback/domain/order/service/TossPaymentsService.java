package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.common.config.TossPaymentsConfig;
import com.homesweet.homesweetback.common.exception.TossApiAuthenticationException;
import com.homesweet.homesweetback.common.exception.TossApiClientException;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

/**
 * 토스페이먼츠 API 연동 서비스 (최소 구현)
 *
 * - 결제 승인 (confirm)
 * - 결제 취소 (cancel)
 * - 결제 조회 (query by paymentKey / orderId)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossPaymentsService {

    private final TossPaymentsConfig tossPaymentsConfig;
    private final RestTemplate restTemplate;

    /**
     * 결제 승인 요청
     */
    public Map<String, Object> confirmPayment(TossPaymentConfirmRequest request) {
        log.info("결제 승인 요청: orderId={}, amount={}", request.getOrderId(), request.getAmount());

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", request.getPaymentKey());
        body.put("orderId", request.getOrderId());
        body.put("amount", request.getAmount());

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, createHeaders());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    tossPaymentsConfig.getConfirmUrl(), httpEntity, Map.class);
            log.info("결제 승인 성공: paymentKey={}", request.getPaymentKey());
            return response;
        } catch (RestClientResponseException e) {
            throw classifyTossException("결제 승인", e);
        }
    }

    /**
     * 결제 취소 요청
     */
    public Map<String, Object> cancelPayment(String paymentKey, TossPaymentCancelRequest request) {
        log.info("결제 취소 요청: paymentKey={}, reason={}", paymentKey, request.getCancelReason());

        Map<String, Object> body = new HashMap<>();
        body.put("cancelReason", request.getCancelReason());
        if (request.getCancelAmount() != null) {
            body.put("cancelAmount", request.getCancelAmount());
        }

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, createHeaders());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    tossPaymentsConfig.getCancelUrl(paymentKey), httpEntity, Map.class);
            log.info("결제 취소 성공: paymentKey={}", paymentKey);
            return response;
        } catch (RestClientResponseException e) {
            throw classifyTossException("결제 취소", e);
        }
    }

    /**
     * paymentKey로 결제 조회
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPaymentByPaymentKey(String paymentKey) {
        log.info("결제 조회 요청: paymentKey={}", paymentKey);

        HttpEntity<Void> httpEntity = new HttpEntity<>(createHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tossPaymentsConfig.getPaymentUrl(paymentKey),
                    HttpMethod.GET, httpEntity, Map.class);
            log.info("결제 조회 성공: paymentKey={}", paymentKey);
            return response.getBody();
        } catch (RestClientResponseException e) {
            throw classifyTossException("결제 조회", e);
        }
    }

    /**
     * orderId로 결제 조회
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPaymentByOrderId(String orderId) {
        log.info("주문ID로 결제 조회 요청: orderId={}", orderId);

        HttpEntity<Void> httpEntity = new HttpEntity<>(createHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tossPaymentsConfig.getOrderIdUrl(orderId),
                    HttpMethod.GET, httpEntity, Map.class);
            log.info("주문ID로 결제 조회 성공: orderId={}", orderId);
            return response.getBody();
        } catch (RestClientResponseException e) {
            throw classifyTossException("결제 조회", e);
        }
    }

    /**
     * Basic 인증 헤더 생성
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String secretKey = tossPaymentsConfig.getSecretKey();
        if (!StringUtils.hasText(secretKey)) {
            throw new TossApiAuthenticationException(
                    "PG 인증 키가 비어 있습니다. payments.toss.secretKey를 확인해주세요.");
        }

        String credentials = secretKey.trim() + ":";
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCredentials);

        return headers;
    }

    private RuntimeException classifyTossException(String action, RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();

        if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            log.error("{} 인증 실패: status={}, body={}", action, exception.getStatusCode(), responseBody);
            return new TossApiAuthenticationException(
                    "PG 인증 실패(401). Toss 시크릿키를 확인해주세요.", exception);
        }

        if (exception.getStatusCode().is4xxClientError()) {
            log.warn("{} 요청 오류: status={}, body={}", action, exception.getStatusCode(), responseBody);
            return new TossApiClientException(action + " 요청 오류: " + responseBody, exception);
        }

        log.error("{} 연동 실패: status={}, body={}", action, exception.getStatusCode(), responseBody);
        return new TossApiClientException(action + " 실패: " + responseBody, exception);
    }
}
