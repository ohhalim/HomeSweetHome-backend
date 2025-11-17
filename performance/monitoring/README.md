# HomeSweetHome Community Domain Monitoring System

현업 수준의 모니터링 스택으로 커뮤니티 도메인의 성능, 로그, 알림을 통합 관리합니다.

## 📊 구성 요소

### 1. **Prometheus** (메트릭 수집)
- Spring Boot Actuator에서 메트릭 수집
- 15초 간격으로 스크랩
- 30일간 데이터 보존
- 포트: **9090**

### 2. **Grafana** (시각화)
- 커뮤니티 도메인 전용 대시보드
- 실시간 메트릭 시각화
- 알림 설정 가능
- 포트: **3000**
- 기본 계정: `admin` / `admin`

### 3. **Loki** (로그 수집)
- 중앙 집중식 로그 관리
- 30일간 로그 보존
- Prometheus와 통합
- 포트: **3100**

### 4. **Promtail** (로그 전송)
- Spring Boot 로그 자동 수집
- 로그 파싱 및 라벨링
- TraceId 추출

### 5. **Alertmanager** (알림 관리)
- 심각도별 알림 라우팅
- Slack, Email, PagerDuty 연동
- 중복 알림 억제
- 포트: **9093**

### 6. **Node Exporter** (시스템 메트릭)
- CPU, 메모리, 디스크 메트릭
- 포트: **9100**

### 7. **cAdvisor** (컨테이너 메트릭)
- Docker 컨테이너 모니터링
- 포트: **8081**

---

## 🚀 빠른 시작

### 1. 사전 준비

**필수 요구사항**:
- Docker 20.10+
- Docker Compose 2.0+
- 최소 4GB RAM
- Spring Boot 애플리케이션이 8080 포트에서 실행 중

**확인**:
```bash
docker --version
docker compose version
```

### 2. Spring Boot 애플리케이션 준비

**application.yml** 또는 **application-dev.yml**에 다음 설정이 있는지 확인:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: homesweet-back
  prometheus:
    metrics:
      export:
        enabled: true
```

**애플리케이션 실행**:
```bash
./gradlew bootRun
```

**Actuator 엔드포인트 확인**:
```bash
curl http://localhost:8080/actuator/prometheus
```

### 3. 모니터링 스택 실행

```bash
cd performance/monitoring
docker compose up -d
```

**컨테이너 상태 확인**:
```bash
docker compose ps
```

모든 컨테이너가 `Up` 상태여야 합니다:
- prometheus
- grafana
- loki
- promtail
- alertmanager
- node-exporter
- cadvisor

### 4. 대시보드 접속

| 서비스 | URL | 계정 |
|--------|-----|------|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| Alertmanager | http://localhost:9093 | - |

---

## 📈 Grafana 대시보드 사용법

### 1. 로그인
1. http://localhost:3000 접속
2. 계정: `admin` / `admin`
3. 첫 로그인 후 비밀번호 변경 권장

### 2. 커뮤니티 대시보드 열기
1. 왼쪽 메뉴 > **Dashboards**
2. **Community Domain** 폴더
3. **HomeSweetHome - Community Domain Performance** 선택

### 3. 대시보드 패널 설명

#### **애플리케이션 상태**
- ✅ UP: 애플리케이션 정상 동작
- ❌ DOWN: 애플리케이션 중단 (즉시 확인 필요)

#### **Requests Per Second (RPS)**
- 초당 요청 수 (전체, 성공, 에러)
- 트래픽 패턴 파악

#### **HTTP Error Rate**
- 5xx 에러 비율
- 5% 이상 시 경고

#### **API Response Time**
- p50, p95, p99 응답 시간
- 성능 저하 감지

#### **Community API Request Rate**
- 게시글 생성/조회
- 댓글 작성
- 좋아요 토글
- 조회수 증가

#### **JVM Heap Memory**
- 힙 메모리 사용량
- 메모리 누수 감지

#### **GC Pause Time**
- Garbage Collection 시간
- GC로 인한 성능 저하 확인

#### **Database Connection Pool**
- 활성/유휴 커넥션
- 커넥션 풀 고갈 감지

#### **CPU & Memory Usage**
- 시스템 리소스 사용률

#### **Top 10 Slowest API Endpoints**
- 가장 느린 엔드포인트 순위
- 최적화 대상 파악

#### **Error Logs**
- 최근 100개 에러 로그
- 실시간 에러 모니터링

### 4. 시간 범위 변경
- 우측 상단의 시간 선택기 사용
- 기본: 최근 1시간
- 옵션: 5m, 15m, 1h, 6h, 24h, 7d 등

### 5. 자동 새로고침
- 우측 상단 새로고침 간격 설정
- 권장: 10초 또는 30초

---

## 🔔 알림 설정

### 1. Slack 연동

**Webhook URL 생성**:
1. Slack Workspace > **Apps** > **Incoming Webhooks**
2. 채널 선택 (예: `#alerts-critical`)
3. Webhook URL 복사

**환경변수 설정**:
```bash
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
```

**Alertmanager 재시작**:
```bash
docker compose restart alertmanager
```

### 2. 알림 테스트

**수동 알림 발송**:
```bash
curl -X POST http://localhost:9093/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '[{
    "labels": {
      "alertname": "TestAlert",
      "severity": "warning"
    },
    "annotations": {
      "summary": "This is a test alert",
      "description": "Testing alert system"
    }
  }]'
```

### 3. 알림 규칙

현재 설정된 알림 규칙 (`prometheus/alerts.yml`):

| 알림 이름 | 조건 | 심각도 | 설명 |
|----------|------|--------|------|
| ApplicationDown | 앱 다운 1분 이상 | Critical | 애플리케이션 중단 |
| HighHttpErrorRate | 5xx 에러율 > 5% | Critical | 높은 HTTP 에러율 |
| SlowResponseTime | p95 > 1초 | Warning | 느린 응답 시간 |
| HighMemoryUsage | 힙 메모리 > 85% | Warning | 높은 메모리 사용률 |
| HighGCTime | GC 시간 > 10% | Warning | 높은 GC 시간 |
| DatabaseConnectionPoolExhausted | 커넥션 > 90% | Critical | 커넥션 풀 고갈 |

---

## 🔍 로그 분석

### 1. Grafana에서 로그 보기

**Explore 메뉴 사용**:
1. 왼쪽 메뉴 > **Explore**
2. 데이터소스: **Loki** 선택
3. 쿼리 입력

**예시 쿼리**:

```logql
# 최근 에러 로그
{job="homesweet-backend",level="ERROR"}

# 커뮤니티 도메인 로그
{job="homesweet-backend",domain="community"}

# 특정 TraceId 로그
{job="homesweet-backend"} |= "TraceId=abc-123"

# 특정 시간 범위의 에러
{job="homesweet-backend",level="ERROR"} | json | line_format "{{.timestamp}} {{.message}}"
```

### 2. 로그 필터링

**심각도별**:
- ERROR: `{level="ERROR"}`
- WARN: `{level="WARN"}`
- INFO: `{level="INFO"}`

**도메인별**:
- 커뮤니티: `{domain="community"}`

**특정 메시지 검색**:
```logql
{job="homesweet-backend"} |= "CommunityPostService"
```

---

## 📊 커스텀 메트릭

Spring Boot 애플리케이션에 추가된 커뮤니티 도메인 전용 메트릭:

### 서비스 레벨 메트릭

| 메트릭 이름 | 설명 | 태그 |
|------------|------|------|
| `community.post.service.*` | 게시글 서비스 실행 시간 | class, method |
| `community.comment.service.*` | 댓글 서비스 실행 시간 | class, method |
| `community.count.service.*` | 좋아요/조회수 서비스 실행 시간 | class, method |
| `community.repository.*` | 리포지토리 쿼리 시간 | class, method |

### 비즈니스 메트릭

| 메트릭 이름 | 설명 | 태그 |
|------------|------|------|
| `community.post.created.total` | 게시글 생성 총 횟수 | operation=create |
| `community.post.viewed.total` | 게시글 조회 총 횟수 | operation=read |
| `community.comment.created.total` | 댓글 생성 총 횟수 | operation=create |
| `community.views.increased.total` | 조회수 증가 총 횟수 | operation=increment |
| `community.post.like.toggled.total` | 좋아요 토글 총 횟수 | operation=toggle |

### 에러 메트릭

| 메트릭 이름 | 설명 | 태그 |
|------------|------|------|
| `community.*.error.count` | 메서드별 에러 횟수 | error_type |
| `community.concurrency.failure.total` | 동시성 제어 실패 | method, exception |
| `community.entity.not_found.total` | 엔티티 미발견 | method, exception |
| `community.authorization.failure.total` | 권한 오류 | method, exception |

### Prometheus에서 메트릭 조회

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

## 🛠️ 문제 해결

### 1. Prometheus가 메트릭을 수집하지 못함

**원인**: Spring Boot 애플리케이션이 실행되지 않거나 Actuator가 비활성화됨

**해결**:
```bash
# 애플리케이션 상태 확인
curl http://localhost:8080/actuator/health

# Prometheus 엔드포인트 확인
curl http://localhost:8080/actuator/prometheus

# Prometheus 타겟 상태 확인
# http://localhost:9090/targets 접속
```

### 2. Grafana 대시보드가 비어있음

**원인**: 데이터소스가 제대로 설정되지 않음

**해결**:
1. Grafana > **Configuration** > **Data Sources**
2. Prometheus 데이터소스 상태 확인
3. **Test** 버튼 클릭
4. 실패 시 URL 확인: `http://prometheus:9090`

### 3. 로그가 Loki에 수집되지 않음

**원인**: Promtail 설정 오류 또는 로그 파일 경로 문제

**해결**:
```bash
# Promtail 로그 확인
docker compose logs promtail

# 로그 파일 경로 확인
ls -la ../../logs/

# Promtail 재시작
docker compose restart promtail
```

### 4. 알림이 전송되지 않음

**원인**: Alertmanager 설정 오류 또는 Webhook URL 문제

**해결**:
```bash
# Alertmanager 로그 확인
docker compose logs alertmanager

# 알림 규칙 확인
# http://localhost:9090/alerts 접속

# Alertmanager 설정 확인
# http://localhost:9093/#/status 접속
```

### 5. 컨테이너가 시작되지 않음

**원인**: 포트 충돌 또는 리소스 부족

**해결**:
```bash
# 포트 사용 확인
sudo lsof -i :3000  # Grafana
sudo lsof -i :9090  # Prometheus
sudo lsof -i :3100  # Loki

# Docker 로그 확인
docker compose logs

# 모든 컨테이너 재시작
docker compose down
docker compose up -d
```

---

## 🧹 유지보수

### 로그 정리

**오래된 데이터 삭제** (30일 이상):
```bash
# Prometheus 데이터 정리 (자동)
# retention.time=30d 설정으로 자동 삭제

# Loki 데이터 정리 (자동)
# retention_period=720h (30일) 설정으로 자동 삭제
```

### 볼륨 백업

```bash
# Prometheus 데이터 백업
docker run --rm \
  -v monitoring_prometheus_data:/data \
  -v $(pwd)/backup:/backup \
  alpine tar czf /backup/prometheus-$(date +%Y%m%d).tar.gz /data

# Grafana 데이터 백업
docker run --rm \
  -v monitoring_grafana_data:/data \
  -v $(pwd)/backup:/backup \
  alpine tar czf /backup/grafana-$(date +%Y%m%d).tar.gz /data
```

### 모니터링 스택 중지

```bash
# 전체 중지 (데이터 보존)
docker compose down

# 전체 삭제 (볼륨 포함)
docker compose down -v
```

---

## 📚 추가 자료

- [Prometheus 공식 문서](https://prometheus.io/docs/)
- [Grafana 공식 문서](https://grafana.com/docs/)
- [Loki 공식 문서](https://grafana.com/docs/loki/latest/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer](https://micrometer.io/docs)

---

## 🤝 기여

모니터링 개선 제안이나 버그 리포트는 이슈로 등록해주세요.

---

## 📝 라이선스

MIT License
