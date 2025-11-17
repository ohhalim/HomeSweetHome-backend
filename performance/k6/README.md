# k6 Performance Testing for Community Domain

커뮤니티 도메인을 위한 현업 수준의 성능 테스트 스크립트입니다.

## 📋 테스트 시나리오

### 1. Load Test (부하 테스트)
**목적**: 일반적인 운영 조건에서 시스템이 예상되는 부하를 처리할 수 있는지 검증

**프로필**:
- 5분: 0 → 100명 사용자 증가
- 10분: 100명 유지
- 5분: 100 → 0명 감소
- **총 소요 시간**: 20분

**실행**:
```bash
k6 run scenarios/load-test.js
```

---

### 2. Stress Test (스트레스 테스트)
**목적**: 시스템의 한계를 찾고 과부하 상황에서의 동작을 검증

**프로필**:
- 2분: 0 → 100명 (워밍업)
- 5분: 100 → 200명 (정상 부하)
- 5분: 200 → 500명 (스트레스)
- 2분: 500명 유지 (임계점)
- 5분: 500 → 0명 (복구)
- **총 소요 시간**: 19분

**실행**:
```bash
k6 run scenarios/stress-test.js
```

---

### 3. Spike Test (스파이크 테스트)
**목적**: 갑작스러운 트래픽 급증 상황에서 시스템의 동작을 검증

**프로필**:
- 1분: 10명 워밍업
- 30초: 10 → 500명 급증 🚀
- 3분: 500명 유지
- 30초: 500 → 10명 급감
- 1분: 10명 유지 (복구)
- 30초: 10 → 1000명 급증 🚀🚀
- 2분: 1000명 유지
- 1분: 1000 → 0명 감소
- **총 소요 시간**: 10분

**실행**:
```bash
k6 run scenarios/spike-test.js
```

---

### 4. Soak Test (내구성 테스트)
**목적**: 장시간 일정한 부하에서 시스템의 안정성을 검증 (메모리 누수, 리소스 고갈 확인)

**프로필**:
- 5분: 0 → 50명 증가
- 1시간: 50명 유지
- 5분: 50 → 0명 감소
- **총 소요 시간**: 1시간 10분

**실행**:
```bash
k6 run scenarios/soak-test.js
```

---

## 🔧 사전 준비

### 1. k6 설치

**macOS**:
```bash
brew install k6
```

**Linux**:
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

**Windows**:
```powershell
choco install k6
```

### 2. 환경변수 설정

테스트를 실행하기 전에 환경변수를 설정하세요:

```bash
# 테스트 대상 서버 URL (선택사항, 기본값: http://localhost:8080)
export BASE_URL=http://localhost:8080

# 테스트용 인증 토큰 (필수)
# 실제 JWT 토큰을 발급받아 사용하거나, 테스트 전용 엔드포인트를 사용하세요
export TEST_TOKEN=your-jwt-token-here
```

### 3. 테스트 데이터 준비

성능 테스트 실행 전에 데이터베이스에 기본 데이터가 있어야 합니다:
- 사용자: 1~100명
- 게시글: 1~100개 (Soak 테스트는 더 많이 필요)
- 댓글: 적당량

---

## 📊 결과 분석

### 주요 메트릭

k6는 다음과 같은 메트릭을 제공합니다:

**기본 메트릭**:
- `http_req_duration`: HTTP 요청 응답 시간
- `http_req_failed`: HTTP 요청 실패율
- `http_reqs`: 초당 요청 수 (RPS)
- `vus`: 가상 사용자 수
- `vus_max`: 최대 가상 사용자 수
- `iterations`: 시나리오 실행 횟수

**커스텀 메트릭**:
- `community_post_creation_duration`: 게시글 생성 시간
- `community_comment_creation_duration`: 댓글 생성 시간
- `community_view_increase_duration`: 조회수 증가 시간
- `community_like_toggle_duration`: 좋아요 토글 시간

### Threshold (임계값)

각 테스트는 다음과 같은 임계값을 설정하고 있습니다:

**Load Test**:
- HTTP 요청 실패율 < 1%
- 95% 요청 < 500ms
- 99% 요청 < 1000ms (읽기), 2000ms (쓰기)
- 체크 성공률 > 99%

**Stress Test**:
- HTTP 요청 실패율 < 5%
- 95% 요청 < 2000ms
- 체크 성공률 > 95%

**Spike Test**:
- HTTP 요청 실패율 < 10%
- 95% 요청 < 5000ms
- 체크 성공률 > 90%

**Soak Test**:
- HTTP 요청 실패율 < 1%
- 95% 요청 < 500ms
- 체크 성공률 > 99.5%

### 결과 내보내기

**JSON 형식**:
```bash
k6 run --out json=results.json scenarios/load-test.js
```

**CSV 형식** (Prometheus를 위한 remote write):
```bash
k6 run --out experimental-prometheus-rw scenarios/load-test.js
```

**Grafana Cloud**:
```bash
k6 run --out cloud scenarios/load-test.js
```

---

## 🎯 테스트 전략

### 권장 실행 순서

1. **Load Test** 먼저 실행
   - 정상적인 부하에서 기준선(baseline) 성능 파악
   - 임계값 조정

2. **Stress Test** 실행
   - 시스템의 한계 파악
   - 병목 지점 식별

3. **Spike Test** 실행
   - 갑작스러운 트래픽 증가 대응 능력 확인
   - 오토스케일링 테스트

4. **Soak Test** 실행 (주말 또는 야간)
   - 장시간 안정성 검증
   - 메모리 누수 확인

### 동시 모니터링

성능 테스트 실행 시 다음을 함께 모니터링하세요:
- Grafana 대시보드
- Prometheus 메트릭
- 애플리케이션 로그 (Loki)
- 데이터베이스 성능
- 시스템 리소스 (CPU, 메모리, 디스크 I/O)

---

## 🔍 문제 해결

### 인증 실패 (401 Unauthorized)

```bash
# 유효한 JWT 토큰을 발급받아 환경변수에 설정
export TEST_TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/test-login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test1234"}' \
  | jq -r '.token')
```

### 연결 실패 (Connection Refused)

서버가 실행 중인지 확인하세요:
```bash
curl http://localhost:8080/actuator/health
```

### 테스트 데이터 부족

테스트 전에 충분한 데이터를 준비하세요:
```bash
# 테스트 데이터 생성 스크립트 실행 (별도 구현 필요)
./scripts/generate-test-data.sh
```

---

## 📚 참고 자료

- [k6 공식 문서](https://k6.io/docs/)
- [k6 Best Practices](https://k6.io/docs/testing-guides/test-types/)
- [Grafana k6 Integration](https://grafana.com/docs/grafana-cloud/k6/)
