# HomeSweetHome Backend Portfolio

> 오늘의집을 벤치마킹한 홈리빙 이커머스 백엔드 프로젝트입니다.  
> 단순 기능 구현을 넘어, 결제/커뮤니티 영역에서 병목과 동시성 문제를 분석하고 개선했습니다.

<img width="975" height="549" alt="HomeSweetHome 대표 이미지" src="https://github.com/user-attachments/assets/57f5dfbf-a606-4bf6-b4f6-c4938a7e3598" />

---

## 📽️ 시연 영상

- [HomeSweetHome 기능 시연 영상 (YouTube)](https://youtu.be/tDZQVn2-uPs?si=hE2dqdOiXLe87Cf)

---

## ✨ Portfolio Highlights

| 영역 | 문제 | 해결 | 성과 |
| --- | --- | --- | --- |
| 결제 안정성 | 중복 결제 시도, 외부 PG 장애 전파 위험 | Redis 멱등성/락 + 보상 취소 + CircuitBreaker/Retry | 결제 흐름 안정화, 중복 승인 방지 구조 확보 |
| 커뮤니티 동시성 | 조회수/좋아요 처리 중 데드락 및 DB 부하 | Atomic Query, Redis + Lua, Write-Behind | 데드락 제거, 고트래픽 처리 구조 전환 |
| 조회 성능 | 목록 조회 시 N+1 및 캐시 비효율 | Bulk MGET, 캐시 무효화 최적화, 인덱스 반영 | Redis 호출 수/쿼리 부하 감소 |

---

## 🧩 내가 집중한 구현

### 1) 결제 도메인

- 주문 단위/결제키 단위 동시성 제어
- 외부 PG 호출과 DB 영속화 경계 분리
- 보상 트랜잭션으로 실패 복구 흐름 구현
- 외부 결제 API 연동 오류 유형 분리 및 복원력 구성

관련 코드:
- `src/main/java/com/homesweet/homesweetback/domain/order/service/PaymentServiceImpl.java`
- `src/main/java/com/homesweet/homesweetback/domain/order/service/PaymentRedisGuardService.java`
- `src/main/java/com/homesweet/homesweetback/domain/order/service/PaymentTransactionalService.java`
- `src/main/java/com/homesweet/homesweetback/domain/order/service/TossPaymentsService.java`

### 2) 커뮤니티 도메인

- 조회수/좋아요/댓글수 카운터를 Redis 기반으로 전환
- Lua Script를 사용한 원자적 토글/카운터 갱신
- 이벤트 큐 + 스케줄러 기반 Write-Behind 동기화
- 캐시 워밍업/무효화 및 목록 조회 최적화

관련 코드:
- `src/main/java/com/homesweet/homesweetback/domain/community/service/CommunityRedisService.java`
- `src/main/java/com/homesweet/homesweetback/domain/community/service/CommunityCountService.java`
- `src/main/java/com/homesweet/homesweetback/domain/community/service/CommunityPostService.java`
- `src/main/java/com/homesweet/homesweetback/domain/community/scheduler/CommunityScheduler.java`
- `src/main/resources/db/migration/V1.0.20__add_community_posts_performance_index.sql`

---

## ⚙️ 개선사항 및 트러블슈팅

### 결제 개선

- Redis 멱등성 키와 주문 락으로 중복 승인 차단
- PG 승인 성공 후 DB 저장 실패 시 보상 취소 처리
- CircuitBreaker/Retry와 예외 분리로 장애 전파 축소
- Mock 결제 서비스로 로컬/테스트 환경 부하 검증 가능

### 커뮤니티 1차 개선: 데드락 해결

- 문제: `PESSIMISTIC_WRITE` 기반 조회+수정 패턴에서 데드락 발생
- 해결: JPQL/네이티브 Atomic Update, 자원 순서화(Resource Ordering)
- 결과: Wiki 부하 테스트 기준 데드락 0회

### 커뮤니티 2차 개선: Redis 카운터 시스템 구축

- 문제: 단순 카운터 연산까지 DB 중심으로 처리되어 병목 발생
- 해결: Redis + Lua Script + Write-Behind + 이벤트 큐
- 결과: 고빈도 카운터 연산을 DB에서 분리

### 커뮤니티 3차 개선: 쿼리/캐시 최적화

- 문제: 목록 조회 시 Redis N+1 호출과 COUNT 부하
- 해결: Bulk MGET, 캐시 전략 개선, 인덱스 반영
- 결과: Wiki 기준 Redis 호출 30회 → 3회(요청당), p95 개선

---

## 📖 Wiki 소개

단순한 기능 개발을 넘어서, 서비스 성능 저하와 장애 발생 원인을 분석하고 직접 해결한 과정들을 심층적으로 정리해 두었습니다.  
문제 원인 파악부터 개선 방안 적용, 검증 과정까지의 기술적 의사결정을 Wiki에 체계적으로 문서화했습니다.

### Wiki 핵심 문서

- 커뮤니티 1차 성능 개선 - 데드락 문제 해결
- 커뮤니티 2차 성능 개선 - Redis 기반 카운터 시스템 구축
- 커뮤니티 3차 성능 개선 - DB 쿼리 최적화 및 캐싱 전략
- 카운터 정합성과 동시성을 유지하며 성능 향상의 어려움

- Wiki 홈: [HomeSweetHome Wiki](https://github.com/ohhalim/HomeSweetHome-backend/wiki)

---

## 📚 기술 스택

- Backend: Java 21, Spring Boot 3.5, Spring Data JPA, QueryDSL
- Security/Auth: Spring Security, OAuth2 (Google/Kakao), JWT
- Data: MySQL, Redis, Flyway
- Infra/Observability: Docker Compose, Actuator, Prometheus, Grafana
- Testing: JUnit5, Testcontainers, k6
- External: Toss Payments, AWS S3

---

## 🧪 테스트 및 부하 테스트

```bash
# 단위/통합 테스트
./gradlew test

# 주문/결제 부하 테스트
k6 run k6/order/local-order-creation-test.js
k6 run k6/order/local-payment-test.js

# 커뮤니티 부하 테스트
k6 run k6/community/v3Quick.js
```

---

## ⚙️ 로컬 실행

```bash
# 1) 인프라 실행
docker compose -f docker-compose.dev.yml up -d

# 2) 애플리케이션 실행 (dev + mock 결제)
PAYMENTS_TOSS_MOCK_ENABLED=true ./gradlew bootRun --args='--spring.profiles.active=dev'

# 3) 모니터링 스택 실행(선택)
docker compose -f docker-compose.monitoring.yml up -d
```

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`

---

## 🔗 프로젝트 링크

| Frontend | Backend |
| --- | --- |
| [FE Github](https://github.com/PRF-JAKODH/HomeSweetHome-front) | [BE Github](https://github.com/ohhalim/HomeSweetHome-backend) |

---

## 👥 팀원

<table align="center">
  <tr>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/ohhalim" width="100" height="100" style="object-fit:cover;"/>
      <br/>오하림<br/>
      <a href="https://github.com/ohhalim">@ohhalim</a>
    </td>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/chaeho5" width="100" height="100" style="object-fit:cover;"/>
      <br/>안채호<br/>
      <a href="https://github.com/chaeho5">@chaeho5</a>
    </td>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/dogyungkim" width="100" height="100" style="object-fit:cover;"/>
      <br/>김도경<br/>
      <a href="https://github.com/dogyungkim">@dogyungkim</a>
    </td>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/Jooahyeon" width="100" height="100" style="object-fit:cover;"/>
      <br/>주아현<br/>
      <a href="https://github.com/Jooahyeon">@Jooahyeon</a>
    </td>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/normaldeve" width="100" height="100" style="object-fit:cover;"/>
      <br/>김준우<br/>
      <a href="https://github.com/normaldeve">@normaldeve</a>
    </td>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/ssooyya" width="100" height="100" style="object-fit:cover;"/>
      <br/>권희수<br/>
      <a href="https://github.com/ssooyya">@ssooyya</a>
    </td>
  </tr>
</table>
