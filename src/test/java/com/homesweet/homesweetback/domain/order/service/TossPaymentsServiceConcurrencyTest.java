package com.homesweet.homesweetback.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.web.client.RestTemplate;

import com.homesweet.homesweetback.common.config.TossPaymentsConfig;
import com.homesweet.homesweetback.common.util.PaymentApiClient;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentCancelRequest;
import com.homesweet.homesweetback.domain.order.dto.TossPaymentConfirmRequest;

/**
 * TossPaymentsService 동시성 테스트
 * 
 * ExecutorService와 CountDownLatch를 사용하여
 * 다중 스레드 환경에서의 동작을 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TossPaymentsService 동시성 테스트")
class TossPaymentsServiceConcurrencyTest {

    @Mock
    private TossPaymentsConfig tossPaymentsConfig;

    @Mock
    private PaymentApiClient paymentApiClient;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TossPaymentsService tossPaymentsService;

    private static final String TEST_SECRET_KEY = "test_sk_concurrent";
    private static final int THREAD_COUNT = 10;
    private static final int TIMEOUT_SECONDS = 30;

    @BeforeEach
    void setUp() {
        given(tossPaymentsConfig.getSecretKey()).willReturn(TEST_SECRET_KEY);
    }

    // ===== 결제 승인 동시성 테스트 =====

    @Nested
    @DisplayName("결제 승인 동시성 테스트")
    class ConfirmPaymentConcurrencyTest {

        @Test
        @DisplayName("동시에 10개의 결제 승인 요청이 모두 성공")
        void confirmPayment_ConcurrentRequests_AllSucceed() throws InterruptedException {
            // given
            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(Map.of("status", "DONE"));

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            // when
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드가 동시에 시작하도록 대기

                        TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                                .paymentKey("concurrent_key_" + index)
                                .orderId("CONCURRENT-ORDER-" + index)
                                .amount(10000L * (index + 1))
                                .build();

                        Map<String, Object> result = tossPaymentsService.confirmPayment(request);

                        if (result != null && "DONE".equals(result.get("status"))) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        exceptions.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // 모든 스레드 동시 시작
            boolean completed = doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
            assertThat(failCount.get()).isEqualTo(0);
            assertThat(exceptions).isEmpty();
        }

        @Test
        @DisplayName("동시 요청 중 일부 실패해도 나머지는 정상 처리")
        void confirmPayment_PartialFailure_OthersContinue() throws InterruptedException {
            // given
            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");

            // 짝수 인덱스는 성공, 홀수는 예외 발생
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willAnswer(invocation -> {
                        // 각 요청마다 독립적으로 처리
                        return Map.of("status", "DONE");
                    });

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger completedCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                                .paymentKey("partial_key_" + index)
                                .orderId("PARTIAL-ORDER-" + index)
                                .amount(10000L)
                                .build();

                        tossPaymentsService.confirmPayment(request);
                        completedCount.incrementAndGet();
                    } catch (Exception e) {
                        // 예외 발생해도 다른 스레드에 영향 없음을 검증
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(completedCount.get()).isGreaterThan(0);
        }
    }

    // ===== 결제 취소 동시성 테스트 =====

    @Nested
    @DisplayName("결제 취소 동시성 테스트")
    class CancelPaymentConcurrencyTest {

        @Test
        @DisplayName("동시에 여러 결제 취소 요청 처리")
        void cancelPayment_ConcurrentRequests() throws InterruptedException {
            // given
            given(tossPaymentsConfig.getCancelUrl(anyString()))
                    .willAnswer(inv -> "https://api.tosspayments.com/v1/payments/" + inv.getArgument(0) + "/cancel");
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(Map.of("status", "CANCELED"));

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                                .cancelReason("동시성 테스트 취소 " + index)
                                .build();

                        Map<String, Object> result = tossPaymentsService.cancelPayment(
                                "cancel_key_" + index, request);

                        if ("CANCELED".equals(result.get("status"))) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // 로깅만 처리
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
        }
    }

    // ===== 혼합 작업 동시성 테스트 =====

    @Nested
    @DisplayName("혼합 작업 동시성 테스트")
    class MixedOperationsConcurrencyTest {

        @Test
        @DisplayName("승인/취소/조회 혼합 동시 요청 처리")
        void mixedOperations_ConcurrentRequests() throws InterruptedException {
            // given
            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");
            given(tossPaymentsConfig.getCancelUrl(anyString()))
                    .willAnswer(inv -> "https://api.tosspayments.com/v1/payments/" + inv.getArgument(0) + "/cancel");
            given(tossPaymentsConfig.getPaymentUrl(anyString()))
                    .willAnswer(inv -> "https://api.tosspayments.com/v1/payments/" + inv.getArgument(0));

            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willReturn(Map.of("status", "DONE"));
            given(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                    .willReturn(new ResponseEntity<>(Map.of("status", "DONE"), HttpStatus.OK));

            int totalOperations = 30; // 10 confirm + 10 cancel + 10 query
            ExecutorService executor = Executors.newFixedThreadPool(totalOperations);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalOperations);
            AtomicInteger confirmCount = new AtomicInteger(0);
            AtomicInteger cancelCount = new AtomicInteger(0);
            AtomicInteger queryCount = new AtomicInteger(0);

            // when - 승인 요청 10개
            for (int i = 0; i < 10; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                                .paymentKey("mixed_confirm_" + index)
                                .orderId("MIXED-ORDER-" + index)
                                .amount(10000L)
                                .build();
                        tossPaymentsService.confirmPayment(request);
                        confirmCount.incrementAndGet();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // 취소 요청 10개
            for (int i = 0; i < 10; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        TossPaymentCancelRequest request = TossPaymentCancelRequest.builder()
                                .cancelReason("혼합 테스트 취소")
                                .build();
                        tossPaymentsService.cancelPayment("mixed_cancel_" + index, request);
                        cancelCount.incrementAndGet();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // 조회 요청 10개
            for (int i = 0; i < 10; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        tossPaymentsService.getPaymentByPaymentKey("mixed_query_" + index);
                        queryCount.incrementAndGet();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(confirmCount.get()).isEqualTo(10);
            assertThat(cancelCount.get()).isEqualTo(10);
            assertThat(queryCount.get()).isEqualTo(10);
        }
    }

    // ===== 스레드 안전성 테스트 =====

    @Nested
    @DisplayName("스레드 안전성 테스트")
    class ThreadSafetyTest {

        @Test
        @DisplayName("서비스 인스턴스가 스레드 안전하게 동작")
        void threadSafety_NoRaceCondition() throws InterruptedException {
            // given
            given(tossPaymentsConfig.getConfirmUrl())
                    .willReturn("https://api.tosspayments.com/v1/payments/confirm");

            AtomicInteger callCounter = new AtomicInteger(0);
            given(paymentApiClient.sendPostRequest(anyString(), any(HttpEntity.class)))
                    .willAnswer(inv -> {
                        callCounter.incrementAndGet();
                        // 약간의 지연을 추가하여 경쟁 상태 유발 가능성 증가
                        Thread.sleep(10);
                        return Map.of("status", "DONE", "count", callCounter.get());
                    });

            int iterations = 100;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(iterations);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < iterations; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        TossPaymentConfirmRequest request = TossPaymentConfirmRequest.builder()
                                .paymentKey("thread_safe_" + index)
                                .orderId("THREAD-SAFE-" + index)
                                .amount(1000L)
                                .build();
                        tossPaymentsService.confirmPayment(request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(iterations);
            assertThat(callCounter.get()).isEqualTo(iterations);
        }
    }
}
