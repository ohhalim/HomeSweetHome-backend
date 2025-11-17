# HomeSweetHome Community Domain - Performance & Monitoring

커뮤니티 도메인을 위한 현업 수준의 성능 테스트 및 모니터링 시스템입니다.

## 📁 디렉토리 구조

```
performance/
├── k6/                          # k6 성능 테스트 스크립트
│   ├── scenarios/              # 테스트 시나리오
│   │   ├── load-test.js       # 부하 테스트 (20분)
│   │   ├── stress-test.js     # 스트레스 테스트 (19분)
│   │   ├── spike-test.js      # 스파이크 테스트 (10분)
│   │   └── soak-test.js       # 내구성 테스트 (1시간 10분)
│   ├── modules/               # 공통 모듈
│   │   ├── config.js         # 설정 및 유틸리티
│   │   ├── auth.js           # 인증 처리
│   │   └── community-api.js  # 커뮤니티 API 호출 함수
│   └── README.md             # k6 테스트 가이드
│
└── monitoring/                 # 모니터링 스택
    ├── docker-compose.yml     # Docker Compose 설정
    ├── prometheus/            # Prometheus 설정
    │   ├── prometheus.yml    # 메트릭 수집 설정
    │   └── alerts.yml        # 알림 규칙
    ├── grafana/              # Grafana 설정
    │   ├── grafana.ini       # Grafana 설정
    │   └── provisioning/     # 자동 프로비저닝
    │       ├── datasources/  # 데이터소스 (Prometheus, Loki)
    │       └── dashboards/   # 대시보드
    ├── loki/                 # Loki 로그 수집
    │   └── loki-config.yml
    ├── promtail/             # Promtail 로그 전송
    │   └── promtail-config.yml
    ├── alertmanager/         # Alertmanager 알림
    │   └── alertmanager.yml
    └── README.md             # 모니터링 가이드
```

---

## 🎯 주요 기능

### 1. **k6 성능 테스트**
- ✅ **Load Test**: 일반적인 부하 테스트 (100명 사용자)
- ✅ **Stress Test**: 시스템 한계 테스트 (500명까지)
- ✅ **Spike Test**: 급격한 트래픽 증가 테스트 (1000명 스파이크)
- ✅ **Soak Test**: 장시간 안정성 테스트 (1시간)

### 2. **현업 수준 모니터링**
- ✅ **Prometheus**: 메트릭 수집 (15초 간격)
- ✅ **Grafana**: 실시간 대시보드
- ✅ **Loki**: 중앙 집중식 로그 관리
- ✅ **Promtail**: 로그 자동 수집
- ✅ **Alertmanager**: 심각도별 알림 (Slack, Email)
- ✅ **Node Exporter**: 시스템 메트릭
- ✅ **cAdvisor**: 컨테이너 메트릭

### 3. **커스텀 메트릭**
- ✅ 게시글/댓글 생성/조회/수정/삭제 시간 및 횟수
- ✅ 좋아요 토글 시간 및 횟수
- ✅ 조회수 증가 시간 및 횟수
- ✅ 동시성 제어 실패 감지
- ✅ 비즈니스 로직 실행 시간 분포 (p50, p95, p99)

---

## 🚀 빠른 시작

### Step 1: 모니터링 스택 실행

```bash
cd performance/monitoring
docker compose up -d
```

**접속 URL**:
- Grafana: http://localhost:3000 (admin / admin)
- Prometheus: http://localhost:9090
- Alertmanager: http://localhost:9093

### Step 2: Spring Boot 애플리케이션 실행

```bash
./gradlew bootRun
```

**메트릭 확인**:
```bash
curl http://localhost:8080/actuator/prometheus
```

### Step 3: k6 성능 테스트 실행

```bash
cd performance/k6

# Load Test (20분)
k6 run scenarios/load-test.js

# Stress Test (19분)
k6 run scenarios/stress-test.js

# Spike Test (10분)
k6 run scenarios/spike-test.js

# Soak Test (1시간 10분)
k6 run scenarios/soak-test.js
```

---

## 📊 Grafana 대시보드

### 주요 패널

1. **애플리케이션 상태**: UP/DOWN
2. **RPS (Requests Per Second)**: 초당 요청 수
3. **HTTP Error Rate**: 5xx 에러율
4. **API Response Time**: p50, p95, p99
5. **Community API 요청 통계**: 게시글, 댓글, 좋아요, 조회수
6. **JVM 메모리**: 힙 메모리 사용량
7. **GC Pause Time**: Garbage Collection 시간
8. **Database Connection Pool**: 커넥션 풀 상태
9. **CPU & Memory**: 시스템 리소스
10. **Top 10 Slowest Endpoints**: 느린 API 순위
11. **Error Logs**: 실시간 에러 로그

### 대시보드 접속
1. http://localhost:3000 접속
2. Dashboards > Community Domain
3. "HomeSweetHome - Community Domain Performance" 선택

---

## 🔔 알림 시스템

### 설정된 알림 규칙

| 알림 | 조건 | 심각도 |
|------|------|--------|
| ApplicationDown | 앱 다운 1분 이상 | Critical |
| HighHttpErrorRate | 5xx 에러율 > 5% | Critical |
| SlowResponseTime | p95 > 1초 | Warning |
| HighPostCreationFailureRate | 게시글 생성 실패율 > 10% | Warning |
| CommentCreationSlow | 댓글 생성 p95 > 2초 | Warning |
| ViewsIncreaseFailureRate | 조회수 증가 실패율 > 5% | Critical |
| HighMemoryUsage | 힙 메모리 > 85% | Warning |
| HighGCTime | GC 시간 > 10% | Warning |
| ThreadDeadlock | 데드락 감지 | Critical |
| DatabaseConnectionPoolExhausted | 커넥션 > 90% | Critical |

### Slack 연동 설정

```bash
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
docker compose restart alertmanager
```

---

## 📈 성능 메트릭

### 커스텀 메트릭 목록

**서비스 레벨**:
- `community.post.service.*` - 게시글 서비스 실행 시간
- `community.comment.service.*` - 댓글 서비스 실행 시간
- `community.count.service.*` - 좋아요/조회수 서비스 실행 시간
- `community.repository.*` - 리포지토리 쿼리 시간

**비즈니스 레벨**:
- `community.post.created.total` - 게시글 생성 총 횟수
- `community.post.viewed.total` - 게시글 조회 총 횟수
- `community.comment.created.total` - 댓글 생성 총 횟수
- `community.views.increased.total` - 조회수 증가 총 횟수
- `community.post.like.toggled.total` - 좋아요 토글 총 횟수

**에러 메트릭**:
- `community.*.error.count` - 메서드별 에러 횟수
- `community.concurrency.failure.total` - 동시성 제어 실패
- `community.entity.not_found.total` - 엔티티 미발견
- `community.authorization.failure.total` - 권한 오류

### Prometheus 쿼리 예시

```promql
# 게시글 생성 속도 (초당)
rate(community_post_created_total[5m])

# 메서드별 평균 실행 시간
avg(community_post_service_createPost_seconds_sum) by (method)

# 에러율
sum(rate(community_post_service_createPost_error_count[5m]))
/ sum(rate(community_post_service_createPost_count[5m]))
```

---

## 🛠️ 성능 테스트 시나리오

### 1. Load Test (부하 테스트)
**목적**: 일반적인 운영 환경에서의 성능 검증

**프로필**:
- 5분: 0 → 100명 증가
- 10분: 100명 유지
- 5분: 100 → 0명 감소

**임계값**:
- HTTP 실패율 < 1%
- p95 < 500ms
- 체크 성공률 > 99%

### 2. Stress Test (스트레스 테스트)
**목적**: 시스템의 한계 파악

**프로필**:
- 2분: 0 → 100명 (워밍업)
- 5분: 100 → 200명 (정상 부하)
- 5분: 200 → 500명 (스트레스)
- 2분: 500명 유지 (임계점)
- 5분: 500 → 0명 (복구)

**임계값**:
- HTTP 실패율 < 5%
- p95 < 2000ms
- 체크 성공률 > 95%

### 3. Spike Test (스파이크 테스트)
**목적**: 갑작스러운 트래픽 급증 대응 검증

**프로필**:
- 1분: 10명 워밍업
- 30초: 10 → 500명 급증 🚀
- 3분: 500명 유지
- 30초: 500 → 10명 급감
- 1분: 10명 유지
- 30초: 10 → 1000명 급증 🚀🚀
- 2분: 1000명 유지
- 1분: 1000 → 0명 감소

**임계값**:
- HTTP 실패율 < 10%
- p95 < 5000ms
- 체크 성공률 > 90%

### 4. Soak Test (내구성 테스트)
**목적**: 장시간 안정성 및 메모리 누수 검증

**프로필**:
- 5분: 0 → 50명 증가
- 1시간: 50명 유지
- 5분: 50 → 0명 감소

**임계값**:
- HTTP 실패율 < 1%
- p95 < 500ms
- 체크 성공률 > 99.5%

---

## 📚 상세 가이드

- [k6 성능 테스트 가이드](./k6/README.md)
- [모니터링 시스템 가이드](./monitoring/README.md)

---

## 🔍 트러블슈팅

### 모니터링이 작동하지 않을 때

```bash
# 1. 컨테이너 상태 확인
docker compose ps

# 2. 로그 확인
docker compose logs prometheus
docker compose logs grafana

# 3. 재시작
docker compose restart
```

### 성능 테스트 실패 시

```bash
# 1. 애플리케이션 상태 확인
curl http://localhost:8080/actuator/health

# 2. 테스트 토큰 확인
export TEST_TOKEN=your-valid-jwt-token

# 3. 베이스 URL 확인
export BASE_URL=http://localhost:8080
```

---

## 🎓 학습 자료

- **k6**: https://k6.io/docs/
- **Prometheus**: https://prometheus.io/docs/
- **Grafana**: https://grafana.com/docs/
- **Loki**: https://grafana.com/docs/loki/
- **Spring Boot Actuator**: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- **Micrometer**: https://micrometer.io/docs

---

## 🤝 기여

성능 개선 제안이나 모니터링 대시보드 개선은 이슈로 등록해주세요.

---

**만든 사람**: HomeSweetHome Team
**날짜**: 2025-01-17
**버전**: 1.0.0
