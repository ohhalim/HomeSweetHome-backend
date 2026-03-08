# 결제 흐름 정리 (Payment Flow)

> 이 문서는 코드를 처음 읽는 사람이 결제 전체 흐름을 이해하기 위한 지도입니다.
> 부하테스트 병목 분석 전에 이 문서를 먼저 읽으세요.

---

## 핵심 파일 목록

| 역할 | 파일 |
|---|---|
| 주문 API | `OrderController.java` |
| 결제 API | `PaymentController.java` |
| 결제 진입점 (Facade) | `PaymentServiceImpl.java` |
| Redis 중복 방지 | `PaymentRedisGuardService.java` |
| Toss API 호출 | `TossPaymentsService.java` |
| DB 저장 (결제 승인) | `PaymentTransactionalService.java` |
| DB 저장 (결제 취소) | `PaymentCancellationTransactionalService.java` |
| 주문 생성 | `OrderServiceImpl.java` |
| 만료 주문 정리 스케줄러 | `PendingOrderExpirationService.java` |
| 취소 정합성 스케줄러 | `PaymentCancellationReconcileScheduler.java` |

---

## 1. 주문 생성 흐름

```
POST /api/v1/orders
  -> OrderController.createOrder()
  -> OrderServiceImpl.createFromCart()
```

### 처리 순서

```
1. 요청 유효성 검사 (수령인, 배송지, 수량)
2. 장바구니 소유권 검증 (내 장바구니의 상품만 주문 가능)
3. 장바구니 ↔ 요청 수량 일치 확인
4. SKU 조회 + 총 금액 계산
5. 재고 차감 (decreaseStock)
   - 실패하면 이미 차감된 항목 재고 복원 후 예외 발생
6. Order, OrderItem 저장
   - OrderItem에 상품명(productName), sourceCartId 스냅샷으로 저장
   - OrderNumber = "ORD-" + UUID (Toss orderId로 사용)
7. Order 상태 = PENDING
```

### 주의사항

- 주문 생성 시점에 **재고가 먼저 빠진다** (선차감)
- 결제를 완료하지 않으면 30분 뒤 스케줄러가 주문을 만료시키고 재고를 복원한다
- `sourceCartId`는 결제 완료 후 장바구니 삭제에 쓰인다

---

## 2. 결제 승인 흐름

```
POST /api/v1/payments/confirm
  -> PaymentController.confirmPayment()
  -> PaymentServiceImpl.confirmPayment()  ← Facade, 트랜잭션 없음
```

### 처리 순서 (PaymentServiceImpl)

```
1. Redis 멱등성 키 획득 (paymentKey 기준)
   - 이미 "IN_PROGRESS" 또는 "DONE" 키가 있으면?
     - DB에서 Payment 조회해서 반환 (중복 요청 처리)
     - 키가 있는데 DB에 없으면 -> "이미 처리 중인 결제" 예외

2. Redis 주문 락 획득 (orderId 기준, UUID 토큰으로 발급)
   - 락 획득 실패 -> 멱등성 키 클리어 후 "이미 처리 중인 결제" 예외

3. Toss API 호출 (트랜잭션 밖)
   -> TossPaymentsService.confirmPayment()
   -> 실제 외부 API 호출 (Toss 서버 -> 결제 승인)

4. DB 저장 (PaymentTransactionalService.persistConfirmedPayment)
   - 주문 소유권 확인
   - 금액 일치 확인
   - 주문 PENDING 상태 확인
   - Payment 레코드 저장 (상태: READY -> DONE)
   - Order 상태: PENDING -> PAID
   - 장바구니에서 sourceCartId 기준으로 사용된 항목 삭제

5. 멱등성 키 "DONE"으로 마킹

6. [예외 발생 시] 보상 취소
   - DB 저장 실패 -> Toss에 취소 요청 (보상 트랜잭션)
   - 멱등성 키 클리어

7. finally: 주문 락 해제
```

### Toss API 호출이 트랜잭션 밖에 있는 이유

Toss API 호출은 네트워크 I/O라서 느리다. 트랜잭션 안에 넣으면 그 시간 동안 DB 커넥션이 점유된다.
그래서 의도적으로 트랜잭션 없이 Toss를 먼저 호출하고, 성공하면 DB를 빠르게 저장한다.

---

## 3. 결제 취소 흐름

```
POST /api/v1/payments/{paymentKey}/cancel
  -> PaymentController.cancelPayment()
  -> PaymentServiceImpl.cancelPayment()
```

### 처리 순서

```
1. DB에서 Payment 조회 + 소유권 확인
   (PaymentCancellationTransactionalService.markCancelRequested)
   - 이미 CANCELLED / PARTIAL_CANCELED -> "이미 취소된 결제" 예외
   - CANCEL_REQUESTED -> 멱등성 처리로 통과 (재시도 안전)
   - DONE 또는 CANCEL_FAILED -> 취소 가능

2. Payment 상태 = CANCEL_REQUESTED (먼저 DB에 마킹)

3. Toss API 호출 (취소 요청)
   -> TossPaymentsService.cancelPayment()

4. 취소 결과 반영 (finalizeCancelSuccess)
   - 전체 취소(CANCELED) -> Payment: CANCELLED, Order: CANCELLED, 재고 복원
   - 부분 취소(PARTIAL_CANCELED) -> Payment: PARTIAL_CANCELED

5. [실패 시] Payment 상태 = CANCEL_FAILED
```

### 상태를 먼저 마킹하는 이유

Toss 호출 전에 `CANCEL_REQUESTED`로 마킹하고, 실패 시 `CANCEL_FAILED`로 바꾼다.
스케줄러가 이 상태를 보고 자동으로 재시도한다.

---

## 4. 스케줄러 2개

### 4-1. 만료 주문 정리 (PendingOrderExpirationScheduler)

```
역할: 결제 안 된 PENDING 주문을 일정 시간 후 자동 취소
주기: 설정값 (order.scheduler.expiry-delay-ms)

처리:
  cutoff 시간 이전에 생성된 PENDING 주문 조회 (배치)
  -> FOR UPDATE 락으로 하나씩 처리
  -> 재고 복원 (increaseStock)
  -> Order 상태 = CANCELLED
```

### 4-2. 취소 정합성 점검 (PaymentCancellationReconcileScheduler)

```
역할: CANCEL_REQUESTED / CANCEL_FAILED 상태인 Payment를 Toss에 재조회해서 실제 취소 여부 판별
주기: 60초 (order.scheduler.cancel-reconcile-delay-ms)

처리:
  CANCEL_REQUESTED / CANCEL_FAILED 상태 Payment 최대 50건 조회
  -> Toss API에서 실제 status 조회
  -> CANCELED / PARTIAL_CANCELED -> finalizeCancelSuccess (재고 복원)
  -> DONE / READY / IN_PROGRESS -> markCancelFailed (취소 실패 확정)
```

---

## 5. Redis 키 구조

| 키 | 값 | TTL | 용도 |
|---|---|---|---|
| `payment:idempotency:{paymentKey}` | `IN_PROGRESS` / `DONE` | 30초 | 결제 중복 방지 |
| `payment:lock:order:{orderId}` | UUID 토큰 | 30초 | 동일 주문 동시 결제 방지 |

락 해제는 Lua 스크립트로 토큰이 일치할 때만 삭제한다 (자기 락만 해제 보장).

---

## 6. 상태 전이 요약

### Order 상태

```
PENDING  --[결제 승인 성공]--> PAID
PENDING  --[취소 or 만료]--> CANCELLED
PAID     --[전체 취소]--> CANCELLED
```

### Payment 상태

```
READY           --[승인 성공]--> DONE
DONE            --[취소 요청]--> CANCEL_REQUESTED
CANCEL_FAILED   --[재시도]--> CANCEL_REQUESTED
CANCEL_REQUESTED --[Toss CANCELED]--> CANCELLED
CANCEL_REQUESTED --[Toss PARTIAL_CANCELED]--> PARTIAL_CANCELED
CANCEL_REQUESTED --[실패]--> CANCEL_FAILED
```

---

## 7. 부하테스트에서 볼 포인트

| 구간 | 병목 가능성 | 확인 지표 |
|---|---|---|
| 재고 차감 (decreaseStock) | DB 락 경합 | DB CPU, 쿼리 응답시간 |
| Redis 락 획득 | Redis 병목 | Redis latency, 락 실패율 |
| Toss API 호출 | 외부 네트워크 | 응답시간, 타임아웃 |
| DB 저장 (Payment+Order) | 커넥션 풀 고갈 | HikariCP pending threads |
| 스케줄러 FOR UPDATE | 다른 트랜잭션과 락 경합 | slow query log |

> Toss 외부 호출은 부하테스트에서 Mock으로 대체해야 서버 병목만 볼 수 있다.
> `MockTossPaymentsService`가 이미 존재한다.

---

## 8. MockTossPaymentsService 사용법

`application-test.yml`에 `payments.toss.mock.enabled: true`이면 Mock이 활성화된다.
부하테스트 시 이 설정을 사용하면 Toss 호출 없이 우리 서버만 테스트할 수 있다.

```yaml
payments:
  toss:
    mock:
      enabled: true
```
