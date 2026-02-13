# IntelliJ에서 Mock Service 활성화 가이드

## 문제 상황
부하 테스트 시 여전히 결제 승인이 500 에러로 실패하고 있습니다.
→ Mock TossPaymentsService가 활성화되지 않음

## 해결 방법: IntelliJ Run Configuration 설정

### 방법 1: Active Profiles 설정 (권장)

1. **Run Configuration 열기**
   - 상단 메뉴: `Run` → `Edit Configurations...`
   - 또는 상단 바에서 실행 설정 드롭다운 클릭 → `Edit Configurations...`

2. **HomesweetBackApplication 선택**
   - 왼쪽 트리에서 `Spring Boot` → `HomesweetBackApplication` 클릭

3. **Active profiles 설정**
   - `Active profiles` 필드를 찾기
   - 값을 `dev`로 입력

4. **Apply → OK**

5. **재시작**
   - 현재 실행 중인 애플리케이션 중지 (빨간 사각형 버튼)
   - 다시 실행 (초록 재생 버튼)

### 방법 2: Environment Variables 설정

Active profiles 필드가 보이지 않는 경우:

1. **Edit Configurations...**
2. **Environment variables** 클릭
3. `+` 버튼 클릭하여 추가:
   ```
   Name: SPRING_PROFILES_ACTIVE
   Value: dev
   ```
4. **Apply → OK**
5. **재시작**

### 방법 3: VM Options 설정

1. **Edit Configurations...**
2. **Modify options** (우측 상단) → **Add VM options** 체크
3. **VM options** 필드에 입력:
   ```
   -Dspring.profiles.active=dev
   ```
4. **Apply → OK**
5. **재시작**

---

## 재시작 후 확인

### 1. 콘솔 로그 확인

IntelliJ Run 탭에서 다음 메시지 확인:
```
🎭 Mock TossPaymentsService 활성화 - 실제 API 호출 없이 테스트합니다
```

또는 프로필 관련 로그:
```
The following profiles are active: dev
```

### 2. Actuator로 확인

터미널에서:
```bash
curl http://localhost:8080/actuator/env | grep -i "spring.profiles.active"
```

**dev가 보이면 성공!**

---

## 그래도 안 될 때

### IntelliJ 캐시 문제

1. **File** → **Invalidate Caches...**
2. **Invalidate and Restart** 클릭
3. IntelliJ 재시작 후 다시 실행

### Gradle 캐시 문제

```bash
./gradlew clean build
```

그 후 IntelliJ에서 재실행

---

## Mock 활성화 확인 방법

### 방법 1: 로그 확인
```
🎭 Mock TossPaymentsService 활성화
```

### 방법 2: 간단한 테스트

새 터미널에서:
```bash
cd k6
k6 run --vus 1 --duration 10s local-payment-test.js
```

**결과가 100% 성공이면** Mock 활성화 완료!

---

## 현재 상태 체크리스트

- [ ] IntelliJ Run Configuration에서 Active profiles = `dev` 설정
- [ ] 애플리케이션 재시작
- [ ] 콘솔에서 "🎭 Mock TossPaymentsService 활성화" 메시지 확인
- [ ] k6 테스트로 검증

---

## 추가 팁

**application.yml의 active profile이 dev여도 IntelliJ Run Configuration이 우선순위가 더 높습니다!**

따라서 반드시 Run Configuration에서 설정해야 합니다.
