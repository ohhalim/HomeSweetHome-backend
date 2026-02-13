# 결제 부하 테스트 결과 분석 및 해결 방안

## 테스트 결과 요약

### ✅ 성공 사항
- **주문 생성**: 100% 성공 (1716건 모두 성공)
- **응답 시간**: 매우 우수
  - p95: 486ms (목표 2초 대비 75% 빠름)
  - 평균: 144ms

### ❌ 실패 사항  
- **결제 승인**: 100% 실패 (1716건 모두 500 에러)
- 에러 메시지: "서버 에러입니다"

---

## 문제 원인

### 근본 원인: 테스트 환경에서 실제 Toss API 호출

```javascript
// k6 테스트 스크립트
paymentKey: `test_payment_${orderNumber}_${Date.now()}`
```

이 가짜 paymentKey를 실제 Toss Payments API로 전송하면:
1. Toss API에서 유효하지 않은 paymentKey로 판단
2. 500 Internal Server Error 반환
3. 백엔드에서 예외 발생

### 코드 흐름

```java
// PaymentServiceImpl.java:74
Map<String, Object> tossResponse = tossPaymentsService.confirmPayment(request);
                                    ↓
// TossPaymentsService.java:53
Map<String, Object> response = paymentApiClient.sendPostRequest(
    tossPaymentsConfig.getConfirmUrl(),  // https://api.tosspayments.com/v1/payments/confirm
    httpEntity
);
```

---

## 해결 방안 3가지

### 방안 1: Profile 기반 Mock 구현 (권장) ⭐

**장점**: 가장 현실적이고 안전  
**구현**: `test` 프로필에서만 Mock 응답 반환

```java
@Profile("test")
@Service
public class MockTossPaymentsService extends TossPaymentsService {
    @Override
    public Map<String, Object> confirmPayment(TossPaymentConfirmRequest request) {
        // Mock 응답 반환
        return Map.of(
            "paymentKey", request.getPaymentKey,
"orderId", request.getOrderId(),
            "status", "DONE",
            "method", "카드",
            "approvedAt", OffsetDateTime.now().toString(),
            "receipt", Map.of("url", "https://mock-receipt.com")
        );
    }
}
```

### 방안 2: PaymentKey Prefix 감지

```java
// TossPaymentsService에 추가
public Map<String, Object> confirmPayment(TossPaymentConfirmRequest request) {
    // 테스트용 paymentKey 감지
    if (request.getPaymentKey().startsWith("test_payment_")) {
        return createMockResponse(request);
    }
    
    // 실제 API 호출
    return paymentApiClient.sendPostRequest(...);
}
```

**단점**: 프로덕션 코드에 테스트 로직 포함

### 방안 3: 부하 테스트를 위한 별도 엔드포인트

```java
@RestController
@RequestMapping("/api/test")
@Profile("dev")
public class TestOrderController {
    
    @PostMapping("/orders/payments/confirm/mock")
    public PaymentResponse mockConfirmPayment(@RequestBody TossPaymentConfirmRequest request) {
        // Mock 결제 승인 (실제 Toss API 호출 없이 DB만 업데이트)
        return paymentService.mockConfirmPayment(request);
    }
}
```

---

## 즉시 테스트 가능한 임시 방안

### 옵션 A: 주문 생성만 테스트

```javascript
// k6/local-order-only-test.js
export default function () {
    // 주문 생성만 수행
    const orderRes = http.post('http://localhost:8080/api/v1/orders', ...);
    
    check(orderRes, {
        '주문 생성 성공': (r) => r.status === 200 || r.status === 201,
    });
    
    // 결제 승인은 스킵
}
```

### 옵션 B: Toss 테스트 키로 실제 테스트

```bash
# Toss Payments 개발자센터에서 실제 테스트 키 받아서 사용
# 단, API 호출 횟수 제한이 있을 수 있음 (분당 100건)
```

---

## 권장 조치

1. **단기 (지금 바로 테스트)**:  
   → 주문 생성 성능만 측정 (이미 100% 성공했으므로 의미 있음)

2. **중기 (이번 주)**: 
   → Mock TossPaymentsService 구현 (Profile 기반)

3. **장기 (다음 주)**: 
   → 통합 테스트 환경 구축 (Testcontainers + WireMock)

---

## 현재 테스트 결과 해석

결제 승인은 실패했지만, **주문 생성 성능은 검증 완료**:

### 주문 생성 API 성능
- **처리량**: 초당 21.3 요청
- **응답 시간**:
  - 평균: 144ms
  - p95: 486ms (목표 2초 대비 우수)
  - 최대: 2022ms

### 시스템 안정성
- **에러율**: 0% (주문 생성)
- **타임아웃**: 없음
- **동시 사용자**: 50명까지 안정적 처리

---

## 다음 액션 아이템

- [ ] Mock TossPaymentsService 구현
- [ ] 결제 승인 포함 전체 플로우 재테스트
- [ ] Grafana 대시보드로 리소스 사용률 확인
- [ ] DB 커넥션 풀, 스레드 풀 튜닝
