# 현업에서 결제 테스트하는 방법

## 1. 환경 분리 전략

### 개발/테스트 환경
```
로컬 → Dev → QA → Staging → Production
```

| 환경 | 결제 방식 | 용도 |
|------|----------|------|
| **Local** | Mock Service | 개발자 로컬 테스트 |
| **Dev** | Mock Service 또는 Sandbox | 기능 개발 |
| **QA** | PG사 Sandbox API | QA 팀 통합 테스트 |
| **Staging** | PG사 Sandbox API | 프로덕션과 동일한 환경에서 최종 검증 |
| **Production** | PG사 실제 API | 실제 결제 |

---

## 2. PG사(결제 대행사) 제공 테스트 환경

### Toss Payments 테스트 환경

**테스트 시크릿 키**:
```
test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6
```

**특징**:
- ✅ 실제 API와 동일한 응답 구조
- ✅ 카드 번호 입력해도 실제 결제 안됨
- ✅ 가상계좌 발급 (입금 시뮬레이션 가능)
- ⚠️ 분당 100건 요청 제한

**테스트 카드 정보**:
- 카드번호: 아무거나 (유효한 형식이면 됨)
- CVC: 123
- 비밀번호: 1234

### Stripe 테스트 환경

**테스트 카드**:
```
카드번호: 4242 4242 4242 4242
만료일: 미래 날짜 아무거나
CVC: 123
```

### PayPal Sandbox

**테스트 계정 생성**:
- PayPal Developer 사이트에서 Buyer/Seller 계정 생성
- 가상 잔액으로 거래 테스트

---

## 3. Mock Service 사용 (우리 프로젝트 방식)

### 장점
- ✅ **API 호출 제한 없음** - 무제한 테스트 가능
- ✅ **빠른 응답** - 네트워크 지연 없음
- ✅ **비용 절감** - PG사 API 호출료 없음
- ✅ **오프라인 개발 가능** - 인터넷 연결 불필요

### 단점
- ❌ 실제 API와 동작 차이 가능
- ❌ PG사 측 에러 시나리오 테스트 불가

### 현업에서의 활용

```java
@Profile("test")  // 테스트 환경
@Service
public class MockPaymentService implements PaymentService {
    // Mock 구현
}

@Profile("qa")    // QA 환경
@Service  
public class SandboxPaymentService implements PaymentService {
    // PG사 Sandbox API 호출
}

@Profile("prod")  // 프로덕션
@Service
public class RealPaymentService implements PaymentService {
    // PG사 실제 API 호출
}
```

---

## 4. 부하 테스트 (우리가 지금 하는 것)

### 테스트 시나리오

```
1. Mock Service로 기능 검증 (빠르게)
   ↓
2. Sandbox API로 실제 플로우 검증 (신중하게)
   ↓
3. Staging에서 최종 검증
   ↓
4. Production 배포
```

### 부하 테스트 시 Mock 사용 이유

```
실제 API 부하 테스트 문제점:
- 분당 100건 제한 → 시스템 한계 테스트 불가
- 높은 API 호출 비용
- PG사 서버에 부담

Mock 사용 시:
- 초당 1000건도 가능
- 순수하게 우리 시스템의 한계 측정
- 비용 없음
```

---

## 5. 실제 PG사 API 테스트 범위

### QA 단계에서 Sandbox로 테스트할 것

1. **Happy Path (정상 플로우)**
   - 카드 결제 성공
   - 가상계좌 발급 및 입금
   - 결제 취소

2. **Error Scenarios**
   - 잔액 부족
   - 카드 정지
   - 한도 초과
   - 네트워크 타임아웃

3. **Edge Cases**
   - 부분 취소
   - 동시 결제 시도
   - 중복 결제 방지

### Staging에서 최종 검증

- 프로덕션과 동일한 환경
- Sandbox API 사용
- 실제 사용자 시나리오 기반 E2E 테스트

---

## 6. 대기업은 어떻게 할까?

### 네이버페이, 카카오페이

```
1. 자체 Mock 서버 구축
2. 단위 테스트: Mock
3. 통합 테스트: Internal Sandbox
4. E2E 테스트: External Sandbox (PG사)
5. 카나리 배포: Production 일부 트래픽만
```

### 쿠팡, 배민

```
- Traffic Shadowing: 프로덕션 트래픽 복제해서 테스트 환경으로
- AB 테스트: 신규 결제 로직을 일부 사용자에게만 적용
- Monitoring: 실시간으로 에러율/성공률 추적
```

---

## 7. 우리 프로젝트 추천 전략

### 단계별 테스트

```
1. 로컬 (지금):
   - Mock Service로 기능 개발
   - k6로 부하 테스트 (Mock)

2. QA:
   - Toss Sandbox API 연동
   - 주요 시나리오 수동 테스트
   - Postman Collection 만들어서 자동화

3. Staging (선택):
   - 프로덕션처럼 구성
   - 최종 검증

4. Production:
   - Canary 배포 (10% 사용자 먼저)
   - 모니터링하며 점진적 확대
```

### 코드 구조

```java
public interface PaymentGateway {
    PaymentResult confirm(PaymentRequest request);
}

@Profile("dev")
class MockPaymentGateway implements PaymentGateway { }

@Profile("qa")
class SandboxPaymentGateway implements PaymentGateway { }

@Profile("prod")
class RealPaymentGateway implements PaymentGateway { }
```

---

## 결론

**현업에서는**:
1. 개발/테스트: **Mock Service** (빠르고 무제한)
2. QA: **PG사 Sandbox** (실제와 유사한 환경)
3. 부하 테스트: **Mock** (시스템 한계 측정)
4. 프로덕션: **실제 API** (점진적 배포)

**우리 프로젝트는 1번 단계를 제대로 하고 있습니다!** 🎉

다음 단계로 Toss Sandbox API 연동해서 실제 플로우도 검증하면 완벽합니다.
