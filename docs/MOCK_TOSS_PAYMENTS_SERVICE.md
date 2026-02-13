# Mock TossPaymentsService 사용 가이드

## 개요

`MockTossPaymentsService`는 테스트 환경에서 실제 Toss Payments API를 호출하지 않고 Mock 응답을 반환하는 서비스입니다.

## 활성화 조건

**자동 활성화되는 프로필**:
- `dev` - 개발 환경
- `test` - 테스트 환경

**비활성화되는 프로필**:
- `prod` - 프로덕션 환경 (실제 TossPaymentsService 사용)

## 동작 방식

### Spring Profile 기반 Bean 교체

```java
@Service
@Primary                    // 우선순위 높음
@Profile({"test", "dev"})  // dev/test 프로필에서만 활성화
public class MockTossPaymentsService extends TossPaymentsService {
    // Mock 구현
}
```

- `@Primary`: 같은 타입의 Bean이 여러 개 있을 때 우선 선택
- `@Profile`: 특정 프로필에서만 Bean 생성

### 프로필별 사용되는 Service

| 프로필 | 사용되는 Service | API 호출 |
|--------|-----------------|----------|
| `dev` | **MockTossPaymentsService** | ❌ Mock 응답 |
| `test` | **MockTossPaymentsService** | ❌ Mock 응답 |
| `prod` | TossPaymentsService | ✅ 실제 API |

## Mock 응답 예시

### 결제 승인 (confirmPayment)

**요청**:
```java
TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
    "test_payment_key_123",
    "order_abc123",
    15000L
);
```

**Mock 응답**:
```json
{
  "version": "2022-11-16",
  "paymentKey": "test_payment_key_123",
  "orderId": "order_abc123",
  "method": "카드",
  "totalAmount": 15000,
  "status": "DONE",
  "approvedAt": "2026-02-12T16:30:00+09:00",
  "card": {
    "number": "123456******1234",
    "approveNo": "00000000"
  },
  "receipt": {
    "url": "https://mockreceipt.tosspayments.com/test_payment_key_123"
  }
}
```

## 사용 방법

### 1. 현재 활성 프로필 확인

```bash
# application.yml 확인
cat src/main/resources/application.yml | grep -A 2 "profiles:"
```

출력:
```yaml
profiles:
  active:
    - dev  # Mock Service 활성화됨
```

### 2. 애플리케이션 재시작

```bash
# 터미널에서 환경변수와 함께 실행
GOOGLE_CLIENT_ID=... \
GOOGLE_CLIENT_SECRET=... \
TOSS_PAYMENTS_SECRET_KEY=test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6 \
./gradlew bootRun
```

**또는 IntelliJ에서 Run** (현재 프로필이 `dev`이므로 자동으로 Mock 활성화)

### 3. k6 부하 테스트 실행

```bash
cd k6
k6 run local-payment-test.js
```

**기대 결과**:
```
✓ 주문 생성 성공 (200/201)     100%
✓ 주문 응답에 orderNumber 포함  100%
✓ 결제 승인 성공 (200/201)     100%  ← 이제 성공!
✓ 결제 응답 파싱 가능           100%

checks: 100% ✅
```

## 로그 확인

Mock Service가 활성화되면 다음과 같은 로그를 볼 수 있습니다:

```
[MOCK] 결제 승인 요청: orderId=abc123, amount=15000
[MOCK] 결제 승인 성공: paymentKey=test_payment_key_123
```

실제 API 호출이 없으므로:
- ✅ API 호출 제한 걱정 없음
- ✅ 무제한 테스트 가능
- ✅ 빠른 응답 속도

## 프로덕션 배포 시 주의사항

### ⚠️ 반드시 prod 프로필 사용

```yaml
# application.yml
spring:
  profiles:
    active:
      - prod  # 프로덕션 배포 시 반드시 prod!
```

또는 환경변수로 설정:
```bash
java -jar app.jar --spring.profiles.active=prod
```

### 검증 방법

```bash
# 로그에서 확인
# Mock 사용 시: [MOCK] 로그 출력
# 실제 API 사용 시: Toss API URL 로그 출력
```

## 트러블슈팅

### 문제: Mock이 활성화되지 않음

**원인**: 프로필이 `prod`로 설정됨

**해결**:
```yaml
# application.yml
spring:
  profiles:
    active:
      - dev  # 또는 test
```

### 문제: 여전히 500 에러 발생

**원인**: Bean 충돌 또는 캐시된 빌드

**해결**:
```bash
# Gradle 클린 빌드
./gradlew clean build

# 애플리케이션 재시작
./gradlew bootRun
```

### 문제: 로그에 [MOCK]이 보이지 않음

**확인 사항**:
1. 프로필 확인: `dev` 또는 `test`인지
2. MockTossPaymentsService 클래스 컴파일 확인
3. IDE 재시작 (클래스 로드 문제일 수 있음)

## 테스트 커버리지

Mock Service로 테스트 가능한 시나리오:

- ✅ 결제 승인 (confirmPayment)
- ✅ 결제 취소 (cancelPayment)
- ✅ paymentKey로 결제 조회 (getPaymentByPaymentKey)
- ✅ orderId로 결제 조회 (getPaymentByOrderId)

## 다음 단계

1. **부하 테스트 재실행**: Mock으로 결제 승인 포함 전체 플로우 테스트
2. **성능 측정**: Grafana로 리소스 사용률 모니터링
3. **한계 테스트**: 동시 사용자 100명 → 500명 → 1000명으로 증가
