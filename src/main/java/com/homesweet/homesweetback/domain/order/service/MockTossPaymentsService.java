package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.common.config.TossPaymentsConfig;
import com.homesweet.homesweetback.common.util.PaymentApiClient;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 테스트 환경용 Mock Toss Payments Service
 * 
 * 실제 Toss Payments API를 호출하지 않고 Mock 응답을 반환합니다.
 * - Profile: test, dev, local (부하 테스트용)
 * - 실제 API 호출 없이 성공 응답 반환
 * - API 호출 제한 없이 무제한 테스트 가능
 * 
 * @Primary 어노테이션으로 실제 TossPaymentsService보다 우선 주입됨
 */
@Slf4j
@Service
@Primary
@Profile({"test", "dev", "local"})
@ConditionalOnProperty(name = "payments.toss.mock.enabled", havingValue = "true")
public class MockTossPaymentsService extends TossPaymentsService {

    // 부모 클래스의 필드를 주입받지만 Mock에서는 사용하지 않음
    public MockTossPaymentsService(TossPaymentsConfig tossPaymentsConfig, 
                                   PaymentApiClient paymentApiClient, 
                                   RestTemplate restTemplate) {
        super(tossPaymentsConfig, paymentApiClient, restTemplate);
        log.info("Mock TossPaymentsService enabled");
    }

    @Override
    public Map<String, Object> confirmPayment(TossPaymentConfirmRequest request) {
        log.info("[MOCK] 결제 승인 요청: orderId={}, amount={}", request.getOrderId(), request.getAmount());

        // Mock 응답 생성
        Map<String, Object> response = new HashMap<>();
        
        // 기본 정보
        response.put("version", "2022-11-16");
        response.put("paymentKey", request.getPaymentKey());
        response.put("type", "NORMAL");
        response.put("orderId", request.getOrderId());
        response.put("orderName", "Mock 주문");
        response.put("mId", "tosspayments");
        response.put("currency", "KRW");
        response.put("method", "카드");
        response.put("totalAmount", request.getAmount());
        response.put("balanceAmount", request.getAmount());
        response.put("status", "DONE");
        response.put("requestedAt", OffsetDateTime.now().toString());
        response.put("approvedAt", OffsetDateTime.now().toString());
        response.put("useEscrow", false);
        response.put("lastTransactionKey", UUID.randomUUID().toString());
        response.put("suppliedAmount", request.getAmount());
        response.put("vat", 0);
        response.put("cultureExpense", false);
        response.put("taxFreeAmount", 0);
        response.put("taxExemptionAmount", 0);
        
        // 카드 정보
        Map<String, Object> card = new HashMap<>();
        card.put("amount", request.getAmount());
        card.put("issuerCode", "41");
        card.put("acquirerCode", "41");
        card.put("number", "123456******1234");
        card.put("installmentPlanMonths", 0);
        card.put("approveNo", "00000000");
        card.put("useCardPoint", false);
        card.put("cardType", "신용");
        card.put("ownerType", "개인");
        card.put("acquireStatus", "READY");
        card.put("isInterestFree", false);
        card.put("interestPayer", null);
        response.put("card", card);
        
        // 영수증
        Map<String, Object> receipt = new HashMap<>();
        receipt.put("url", "https://mockreceipt.tosspayments.com/" + request.getPaymentKey());
        response.put("receipt", receipt);
        
        // 체크아웃
        Map<String, Object> checkout = new HashMap<>();
        checkout.put("url", "https://mockcheckout.tosspayments.com/" + request.getPaymentKey());
        response.put("checkout", checkout);
        
        // 간편결제 정보
        response.put("easyPay", null);
        
        // 국가 코드
        response.put("country", "KR");
        
        // 실패 정보 (null)
        response.put("failure", null);
        
        // 취소 내역
        response.put("cancels", null);
        
        // 할인 정보
        response.put("discount", null);

        log.info("[MOCK] 결제 승인 성공: paymentKey={}", request.getPaymentKey());
        return response;
    }

    @Override
    public Map<String, Object> cancelPayment(String paymentKey, TossPaymentCancelRequest request) {
        log.info("[MOCK] 결제 취소 요청: paymentKey={}, reason={}", paymentKey, request.getCancelReason());

        Map<String, Object> response = new HashMap<>();
        response.put("paymentKey", paymentKey);
        response.put("status", "CANCELED");
        response.put("cancelReason", request.getCancelReason());
        response.put("canceledAt", OffsetDateTime.now().toString());
        
        if (request.getCancelAmount() != null) {
            response.put("cancelAmount", request.getCancelAmount());
        }

        log.info("[MOCK] 결제 취소 성공: paymentKey={}", paymentKey);
        return response;
    }

    @Override
    public Map<String, Object> getPaymentByPaymentKey(String paymentKey) {
        log.info("[MOCK] 결제 조회 요청: paymentKey={}", paymentKey);

        Map<String, Object> response = new HashMap<>();
        response.put("paymentKey", paymentKey);
        response.put("status", "DONE");
        response.put("method", "카드");
        response.put("approvedAt", OffsetDateTime.now().toString());

        log.info("[MOCK] 결제 조회 성공: paymentKey={}", paymentKey);
        return response;
    }

    @Override
    public Map<String, Object> getPaymentByOrderId(String orderId) {
        log.info("[MOCK] 주문ID로 결제 조회 요청: orderId={}", orderId);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("status", "DONE");
        response.put("method", "카드");
        response.put("approvedAt", OffsetDateTime.now().toString());

        log.info("[MOCK] 주문ID로 결제 조회 성공: orderId={}", orderId);
        return response;
    }
}
