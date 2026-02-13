# 모니터링 및 부하 테스트 가이드

## 사전 준비

### 1. 필수 도구 설치

```bash
# k6 설치 (Mac)
brew install k6

# Docker 및 Docker Compose 설치 확인
docker --version
docker-compose --version
```

### 2. 애플리케이션 실행

```bash
# 백엔드 애플리케이션 실행
GOOGLE_CLIENT_ID=1091815838819-63ftv8b7ihgiqh62ru48v35b60voqh9v.apps.googleusercontent.com \
GOOGLE_CLIENT_SECRET=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 \
TOSS_PAYMENTS_SECRET_KEY=test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6 \
./gradlew bootRun

# 또는 IntelliJ에서 Run (application.yml의 active profile이 dev로 설정되어 있어야 함)
```

---

## 모니터링 설정

### 1. Prometheus + Grafana 실행

```bash
# 모니터링 스택 시작
docker-compose -f docker-compose.monitoring.yml up -d

# 로그 확인
docker-compose -f docker-compose.monitoring.yml logs -f
```

### 2. 접속 확인

- **Prometheus**: http://localhost:9090
  - Status → Targets에서 메트릭 수집 상태 확인
  - `spring-actuator`, `node_exporter`, `mysqld_exporter` 모두 UP 상태여야 함

- **Grafana**: http://localhost:3001
  - 초기 로그인: `admin` / `admin`
  - 데이터 소스 추가: Configuration → Data Sources → Prometheus
  - URL: `http://prometheus:9090`

### 3. 대시보드 생성

**추천 Grafana 대시보드 ID**:
- **Spring Boot 2.1 System Monitor**: 11378
- **JVM (Micrometer)**: 4701
- **MySQL Overview**: 7362

Import 방법:
1. Grafana → Create → Import
2. Dashboard ID 입력 후 Load
3. Prometheus 데이터 소스 선택

---

## 부하 테스트 실행

### 1. 상품 조회 테스트

```bash
cd k6
k6 run local-product-test.js

# 결과를 HTML로 저장
k6 run --out json=results.json local-product-test.js
```

**테스트 시나리오**:
- 30초: 0 → 50 VU (가상 사용자)
- 1분: 50 → 100 VU
- 2분: 100 VU 유지
- 30초: 100 → 0 VU

### 2. 결제 플로우 테스트

```bash
k6 run local-payment-test.js

# 상세 로그와 함께 실행
k6 run --verbose local-payment-test.js
```

**테스트 시나리오**:
- 주문 생성 → 결제 승인 전체 플로우
- 30초: 0 → 10 VU
- 1분: 10 → 50 VU  
- 2분: 50 VU 유지
- 30초: 50 → 0 VU

**성능 목표**:
- ✅ 95% 요청이 3초 이내 응답
- ✅ 주문 생성 95%가 2초 이내
- ✅ 결제 승인 95%가 2초 이내
- ✅ 전체 성공률 95% 이상

### 3. 실시간 모니터링하며 테스트

**터미널 1**: Grafana 대시보드 열기 (http://localhost:3001)

**터미널 2**: 부하 테스트 실행
```bash
k6 run local-payment-test.js
```

**모니터링 포인트**:
- HTTP 요청 지연 시간 (p50, p95, p99)
- 에러율
- JVM 힙 메모리 사용량
- GC 빈도 및 시간
- DB 커넥션 풀 사용률

---

## 결과 분석

### k6 출력 읽는 법

```
✓ 주문 생성 성공 (200/201)....: 100.00% ✓ 500  ✗ 0
✓ 결제 승인 성공 (200/201).....: 98.50%  ✓ 493  ✗ 7

http_req_duration..............: avg=1.2s   p(95)=2.5s  p(99)=3.2s
order_creation_duration........: avg=850ms  p(95)=1.8s  
payment_confirm_duration.......: avg=1.1s   p(95)=2.3s  
```

**해석**:
- **체크 성공률**: 주문 100%, 결제 98.5% → 결제에서 1.5% 실패 발생
- **평균 응답 시간**: 1.2초 (양호)
- **p95**: 95%의 요청이 2.5초 이내 → 목표(3초) 달성
- **병목 가능성**: 결제 승인이 주문 생성보다 느림

### Grafana에서 확인할 사항

1. **CPU 사용률 급증 구간**: 병목 지점
2. **메모리 누수**: 힙 메모리가 계속 증가만 하는지
3. **DB 쿼리 지연**: Slow Query 발생 여부
4. **스레드 풀 상태**: Active 스레드가 max에 도달했는지

---

## 문제 해결

### 문제 1: Prometheus가 메트릭 수집 실패

**증상**: Targets 페이지에서 `DOWN` 상태

**해결**:
```bash
# 백엔드 애플리케이션의 actuator 엔드포인트 확인
curl http://localhost:8080/actuator/prometheus

# 응답이 없으면 application.yml 확인
# management.endpoints.web.exposure.include에 prometheus 포함 확인
```

### 문제 2: k6 테스트 시 401 Unauthorized

**원인**: JWT 토큰 또는 인증 문제

**해결**:
1. 테스트 사용자 ID(1~5)가 DB에 존재하는지 확인
2. Spring Security 설정에서 테스트용 엔드포인트 permitAll 추가 고려

### 문제 3: 결제 승인 500 에러

**원인**: Toss Payments Secret Key 누락 또는 잘못됨

**해결**:
```bash
# 환경변수 확인
echo $TOSS_PAYMENTS_SECRET_KEY

# .env 파일 또는 실행 시 환경변수 전달 확인
```

---

## 다음 단계

1. **병목 지점 특정**: Grafana + k6 결과로 어느 레이어에서 지연 발생하는지 파악
2. **최적화 적용**: 
   - DB 인덱스 추가
   - 캐싱 전략 개선
   - 커넥션 풀 크기 조정
3. **재테스트**: 개선 후 동일한 부하로 재테스트하여 효과 검증
4. **한계 테스트**: VU를 점진적으로 증가시켜 시스템 한계점 파악
