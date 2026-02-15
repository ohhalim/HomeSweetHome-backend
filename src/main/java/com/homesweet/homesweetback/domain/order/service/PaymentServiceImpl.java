package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.domain.order.dto.PaymentResponse;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;
import com.homesweet.homesweetback.domain.order.entity.Payment;
import com.homesweet.homesweetback.domain.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Payment facade.
 *
 * confirmPayment:
 * 1) acquire redis idempotency/order lock
 * 2) call external Toss API without DB transaction
 * 3) persist payment/order state in transactional service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final TossPaymentsService tossPaymentsService;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionalService paymentTransactionalService;
    private final PaymentRedisGuardService paymentRedisGuardService;

    @Override
    public PaymentResponse confirmPayment(Long userId, TossPaymentConfirmRequest request) {
        String paymentKey = request.getPaymentKey();
        String orderId = request.getOrderId();

        log.info("Payment confirm started. userId={}, orderId={}, amount={}",
                userId, orderId, request.getAmount());

        if (!paymentRedisGuardService.tryAcquireIdempotency(paymentKey)) {
            return paymentRepository.findByPaymentKey(paymentKey)
                    .map(PaymentResponse::from)
                    .orElseThrow(() -> new IllegalStateException("이미 처리 중인 결제 요청입니다."));
        }

        String orderLockToken = paymentRedisGuardService.tryAcquireOrderLock(orderId);
        if (orderLockToken == null) {
            paymentRedisGuardService.clearIdempotency(paymentKey);
            throw new IllegalStateException("이미 해당 주문의 결제가 처리 중입니다.");
        }

        boolean tossApproved = false;
        try {
            Map<String, Object> tossResponse = tossPaymentsService.confirmPayment(request);
            tossApproved = true;

            PaymentResponse response = paymentTransactionalService.persistConfirmedPayment(userId, request, tossResponse);
            paymentRedisGuardService.markIdempotencyCompleted(paymentKey);

            log.info("Payment confirm completed. paymentKey={}, orderId={}", paymentKey, orderId);
            return response;

        } catch (Exception e) {
            paymentRedisGuardService.clearIdempotency(paymentKey);

            if (tossApproved) {
                cancelPaymentForCompensation(paymentKey);
            }
            throw e;

        } finally {
            paymentRedisGuardService.releaseOrderLock(orderId, orderLockToken);
        }
    }

    private void cancelPaymentForCompensation(String paymentKey) {
        try {
            TossPaymentCancelRequest cancelRequest = new TossPaymentCancelRequest("시스템 보상 취소: DB 처리 실패", null);
            tossPaymentsService.cancelPayment(paymentKey, cancelRequest);
            log.info("Compensation cancel succeeded. paymentKey={}", paymentKey);
        } catch (Exception cancelEx) {
            log.error("Compensation cancel failed. paymentKey={}", paymentKey, cancelEx);
        }
    }

    @Override
    @Transactional
    public PaymentResponse cancelPayment(Long userId, String paymentKey, TossPaymentCancelRequest request) {
        log.info("Payment cancel started. userId={}, paymentKey={}", userId, paymentKey);

        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        if (!payment.getOrder().isOwner(userId)) {
            throw new IllegalArgumentException("본인의 결제만 취소할 수 있습니다.");
        }

        tossPaymentsService.cancelPayment(paymentKey, request);

        if (request.getCancelAmount() != null && request.getCancelAmount() < payment.getAmount()) {
            payment.partialCancel();
        } else {
            payment.cancel();
            payment.getOrder().cancel();
        }

        paymentRepository.save(payment);
        log.info("Payment cancel completed. paymentKey={}", paymentKey);

        return PaymentResponse.from(payment);
    }

    @Override
    public PaymentResponse getPayment(Long userId, String paymentKey) {
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        if (!payment.getOrder().isOwner(userId)) {
            throw new IllegalArgumentException("본인의 결제만 조회할 수 있습니다.");
        }

        return PaymentResponse.from(payment);
    }
}
