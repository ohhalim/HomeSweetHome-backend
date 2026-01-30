package com.homesweet.homesweetback.domain.subscription.service;

import com.homesweet.homesweetback.common.config.TossPaymentsConfig;
import com.homesweet.homesweetback.common.exception.TossApiFailedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 토스페이먼츠 빌링(자동결제) API 연동 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossBillingService {

    private final TossPaymentsConfig tossPaymentsConfig;
    private final RestTemplate restTemplate;

    private static final String BILLING_KEY_ISSUE_URL = "https://api.tosspayments.com/v1/billing/authorizations/issue";
    private static final String BILLING_PAYMENT_URL = "https://api.tosspayments.com/v1/billing/";

    /**
     * 빌링키 발급 (카드 등록 후)
     * 프론트에서 카드 등록 완료 후 받은 authKey로 빌링키 발급
     *
     * @param authKey     토스에서 전달받은 인증 키
     * @param customerKey 고객 고유 키 (가맹점에서 생성)
     * @return 발급된 빌링키 정보
     */
    @CircuitBreaker(name = "toss-payments", fallbackMethod = "issueBillingKeyFallback")
    public Map<String, Object> issueBillingKey(String authKey, String customerKey) {
        log.info("빌링키 발급 요청: customerKey={}", customerKey);

        Map<String, Object> body = new HashMap<>();
        body.put("authKey", authKey);
        body.put("customerKey", customerKey);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, createHeaders());

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    BILLING_KEY_ISSUE_URL,
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            log.info("빌링키 발급 성공: customerKey={}", customerKey);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("빌링키 발급 실패: {}", e.getResponseBodyAsString());
            throw new TossApiFailedException("빌링키 발급 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 빌링키로 자동결제 승인
     * 스케줄러나 첫 결제 시 호출
     *
     * @param billingKey  발급받은 빌링키
     * @param customerKey 고객 고유 키
     * @param amount      결제 금액
     * @param orderId     주문 ID
     * @param orderName   주문명
     * @return 결제 결과
     */
    @CircuitBreaker(name = "toss-payments", fallbackMethod = "billingPaymentFallback")
    public Map<String, Object> requestBillingPayment(
            String billingKey,
            String customerKey,
            Long amount,
            String orderId,
            String orderName) {
        log.info("빌링 결제 요청: orderId={}, amount={}", orderId, amount);

        Map<String, Object> body = new HashMap<>();
        body.put("customerKey", customerKey);
        body.put("amount", amount);
        body.put("orderId", orderId);
        body.put("orderName", orderName);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, createHeaders());

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    BILLING_PAYMENT_URL + billingKey,
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            log.info("빌링 결제 성공: orderId={}", orderId);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("빌링 결제 실패: {}", e.getResponseBodyAsString());
            throw new TossApiFailedException("빌링 결제 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Basic 인증 헤더 생성
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String credentials = tossPaymentsConfig.getSecretKey() + ":";
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCredentials);

        return headers;
    }

    // ===== Fallback 메서드 (Resilience4j CircuitBreaker에서 사용) =====

    @SuppressWarnings("unused")
    private Map<String, Object> issueBillingKeyFallback(String authKey, String customerKey, Throwable t) {
        log.error("빌링키 발급 서킷브레이커 작동: customerKey={}, error={}", customerKey, t.getMessage());
        throw new TossApiFailedException("결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", t);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> billingPaymentFallback(
            String billingKey, String customerKey, Long amount, String orderId, String orderName, Throwable t) {
        log.error("빌링 결제 서킷브레이커 작동: orderId={}, error={}", orderId, t.getMessage());
        throw new TossApiFailedException("결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", t);
    }
}
