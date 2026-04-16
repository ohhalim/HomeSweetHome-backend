# 병목을 측정하고 구조적으로 개선한 이커머스 백엔드 프로젝트, HomeSweetHome

<img width="975" height="549" alt="HomeSweetHome 대표 이미지" src="https://github.com/user-attachments/assets/57f5dfbf-a606-4bf6-b4f6-c4938a7e3598" />

---

## 📽️ 시연영상

https://www.youtube.com/watch?v=tDZQVn2-uPs

---

## 🔖 프로젝트 개요

- 국내 홈리빙 이커머스 서비스를 벤치마킹해 주문·결제와 커뮤니티 핵심 기능을 구현하고, 병목 구간을 실측 기반으로 개선한 백엔드 프로젝트입니다.
- 단순 기능 구현에 그치지 않고, `동시성`, `정합성`, `성능`, `운영 관측성` 문제를 재현하고 구조적으로 해결하는 과정을 프로젝트 중심에 두었습니다.
- 개발 방식은 `기획 및 MVP 구축 → 단위/통합/부하 테스트로 병목 재현 → 원인 분석 → 구조 개선 → 재측정` 사이클로 운영했습니다.

---

## 🙋 제가 맡아 집중한 영역

- **주문/결제** : 재고 차감 경로 최적화, 결제 멱등성, 주문 단위 락, 보상 취소, 결제 취소 재고 정합성 개선
- **커뮤니티** : 조회수/댓글수/좋아요 카운터 구조 개선, 데드락 해소, Redis 캐시·배치 동기화·벌크 조회 최적화
- **성능/운영** : k6 부하 테스트, Grafana/HikariCP/DB `PROCESSLIST` 기반 병목 분석, Tomcat backlog 및 커넥션 풀 튜닝

---

## 📈 대표 성과

- 주문 재고 차감 경로의 DB row lock 병목을 Redis 원자 연산 기반 재고 캐시로 전환해 주문 생성 p95를 **1.68s → 1.09s**로 줄이고, 처리량을 **241 → 401 req/s(66%)** 개선
- 주문 목록 조회 API의 전체 `JOIN FETCH` 구조를 페이징 + 2단계 조회 방식으로 리팩터링해 `order_list` p95를 **712ms → 25.9ms(약 96%)** 개선
- `AFTER_COMMIT + REQUIRES_NEW` 기반 재고 동기화에서 발생한 커넥션 풀 고갈 문제를 `@Async` 기반 비동기 DB sync 구조로 재설계해 주문 생성 p95를 **58.3s → 552ms**, 오류율을 **14.8% → 0%**로 안정화
- 결제 전체 취소 시 재고 복원이 누락되던 버그를 수정해 취소 후 재고가 영구 차감되는 문제를 해결하고, 주문/결제 취소 경로의 재고 정합성을 보강

---

## 🧠 이 프로젝트에서 보여주고 싶은 역량

- **원인 분석 능력** : 성능 저하를 단순히 “느리다” 수준에서 보지 않고, `row lock`, `deadlock`, `connection pool contention`, `TCP backlog`처럼 시스템 레벨 원인까지 좁혀 해결했습니다.
- **구조 개선 능력** : Redis 원자 연산, Lua Script, Cache-Aside, Write-Behind, 비동기 이벤트, 페이징 + 2단계 조회처럼 문제에 맞는 구조를 선택해 적용했습니다.
- **측정 기반 개선 방식** : k6, Grafana, DB `PROCESSLIST`, HikariCP, Spring Actuator를 함께 활용해 가설 수립 → 재현 → 개선 → 재검증 사이클을 반복했습니다.

---

## 📚 기술 스택

<img width="1046" height="590" alt="기술 스택" src="https://github.com/user-attachments/assets/1d9897c2-2858-4e1b-aaee-341de7c62f0a" />

---

## 🌏 서버 아키텍쳐

<img width="978" height="582" alt="서버 아키텍처" src="https://github.com/user-attachments/assets/8dbba002-28ad-4461-be66-2e56a626f891" />

---

## 🔗 제가 구현·개선한 핵심 기능

### 1️⃣ 주문 및 결제 시스템

- 단일/다건 주문 생성 : 장바구니 기반으로 여러 상품을 한 번에 주문할 수 있습니다.
- 결제 승인/취소/조회 : Toss 결제 흐름(주문 생성 → 결제 승인 → 결제 조회/취소)을 API로 제공합니다.
- 결제 멱등성 보장 : `paymentKey` 기준 멱등성 키를 사용해 중복 승인 요청을 차단합니다.
- 주문 단위 동시성 제어 : `orderId` 기준 Redis 락으로 동일 주문의 동시 결제 시도를 제어합니다.
- 결제 정합성 유지 : 외부 승인 후 내부 저장 실패 시 보상 취소(Compensation)로 상태 불일치를 방지합니다.

### 2️⃣ 커뮤니티 시스템

- 게시글/댓글 기능 : 게시글·댓글 CRUD 및 좋아요 토글 기능을 제공합니다.
- 실시간 카운터 : 조회수/좋아요수/댓글수를 Redis 카운터로 처리합니다.
- 원자적 동시성 제어 : Lua Script로 좋아요 토글/카운터 증감을 원자적으로 수행합니다.
- 비동기 동기화 : Event Queue + Scheduler 기반 Write-Behind 패턴으로 Redis 데이터를 DB에 동기화합니다.
- 조회 최적화 : Bulk MGET, 캐시 워밍업, 캐시 무효화(SCAN) 전략을 적용합니다.

---

## 🚀 개선 사항

### 1️⃣ 결제 시스템 1차 개선 - 동시성 제어 및 정합성 강화

#### 문제 상황

- 동일 결제 요청이 중복으로 들어오면 중복 승인/중복 처리 위험이 존재
- 외부 결제 승인 성공 후 내부 DB 처리 실패 시 결제 상태와 주문 상태가 불일치
- 고동시성 구간에서 결제 흐름 안정성이 낮음

#### 해결 방법

- Redis 멱등성 키(`payment:idempotency:*`) 도입으로 동일 `paymentKey` 중복 처리 차단
- 주문 락(`payment:lock:order:*`) 도입으로 동일 주문 결제 요청 직렬화
- 락 해제는 Lua Script 기반 토큰 검증 방식으로 안전하게 처리
- 외부 승인 성공 후 DB 저장 실패 시 결제 취소 보상 트랜잭션 적용

#### 결과

- 중복 결제 승인 방지 구조 확보
- 외부 결제 상태와 내부 주문 상태 정합성 강화
- 결제 실패 케이스에서 복구 가능성 확보

---

### 2️⃣ 결제 시스템 2차 개선 - 외부 PG 장애 대응 및 테스트 환경 개선

#### 문제 상황

- PG 연동 오류가 단일 500 응답으로 처리되어 원인 식별이 어려움
- 실PG 호출 제약으로 로컬/부하 테스트 반복 검증이 어려움

#### 해결 방법

- Toss 연동 예외를 인증 오류(401), 클라이언트 오류(4xx), 연동 실패(5xx/기타)로 분리
- Resilience4j Circuit Breaker + Retry 정책 적용
- dev/test 환경에서 Mock TossPaymentsService를 사용하도록 분리

#### 결과

- 장애 유형별 대응 전략(재시도/즉시 실패/관측) 적용 가능
- 테스트 환경에서 결제 플로우 반복 검증 가능
- 결제 장애 분석 속도와 운영 안정성 향상


---

### 3️⃣ 주문 시스템 1차 성능 개선 - 재고 차감 경로 병목 제거

#### 문제 상황

- 단일 SKU 동시 주문 시 `UPDATE sku SET stock_quantity = stock_quantity - 1 ...` 쿼리에 InnoDB row lock이 집중
- 재고 차감 쿼리가 직렬화되면서 커넥션 점유 시간이 길어지고, 주문 생성 p95가 **1.68s**까지 증가
- HikariCP Pending과 DB 대기 쿼리가 함께 증가하며 처리량이 제한됨

#### 해결 방법

- `StockCacheService`를 도입해 주문 재고 차감/복원을 Redis `DECRBY`/`INCRBY` 기반 원자 연산으로 전환
- `OrderServiceImpl.reserveStock()` / `restoreStock()`에서 DB UPDATE를 제거하고 Redis만 hot path에서 사용
- Redis key miss 시 DB 값을 1회 로드하는 lazy init 적용
- `SkuJPARepository.decreaseStockDirect()`를 추가해 이후 비동기 DB sync 경로를 준비

#### 결과

- 주문 생성 p95 개선 : **1.68s → 1.09s**
- 처리량 개선 : **241 → 401 req/s (66%)**
- DB row lock 대기와 커넥션 점유 시간이 감소하며 주문 생성 경로 병목 완화

---

### 4️⃣ 주문 시스템 2차 성능 개선 - 비동기 재고 동기화로 커넥션 풀 고갈 해결

#### 문제 상황

- Redis만 재고를 갱신하면서 DB `stock_quantity`가 stale 상태로 남아 서버 재시작 시 재고가 부풀 수 있는 구조적 문제가 존재
- `AFTER_COMMIT + REQUIRES_NEW` 방식으로 DB 동기화를 시도했지만, Spring 커밋 라이프사이클상 기존 커넥션 반환 전 새 커넥션을 요청해 HikariCP 풀이 고갈
- 단일 SKU 집중 부하에서 주문 생성 p95가 **58.3s**, 오류율이 **14.8%**까지 악화

#### 해결 방법

- Spring commit 순서를 분석해 병목 원인을 `connection pool contention`으로 특정
- `@Async` 기반 `AFTER_COMMIT` 재고 동기화 구조로 변경해 요청 스레드와 DB sync 스레드를 분리
- `PaymentServiceImpl` full cancel 경로에 Redis 재고 복원 + 이벤트 발행을 추가해 재고 복원 누락 버그 수정
- k6, Grafana, DB `PROCESSLIST`, HikariCP 메트릭을 함께 활용해 재현과 검증을 반복

#### 결과

- 주문 생성 p95 개선 : **58.3s → 552ms**
- 오류율 개선 : **14.8% → 0%**
- `http p99` : **761ms**, HikariCP Pending : **0**
- 주문/결제 취소 경로의 재고 정합성 보강

---

### 5️⃣ 커뮤니티 1차 성능 개선 - 데드락 문제 해결

#### 문제 상황

- `PESSIMISTIC_WRITE` 기반 조회 후 수정 패턴에서 데드락 발생
- k6 부하 테스트 중 `Deadlock found when trying to get lock` 오류가 빈번히 발생
- FK 제약이 있는 부모/자식 테이블 락 순서 불일치로 충돌 발생

#### 해결 방법

- `SELECT FOR UPDATE` 후 엔티티 수정 방식 제거
- JPQL/네이티브 Atomic UPDATE/DELETE로 직접 갱신
- Resource Ordering 적용: 부모(posts) 먼저, 자식(post_likes) 나중 처리
- `@Modifying(clearAutomatically = true, flushAutomatically = true)` 적용

#### 결과

- 데드락 발생 빈도 개선 : **빈번 발생 → 0회**
- 동시성 에러 응답 감소
- 카운터 갱신 안정성 확보

개선 전  
![커뮤니티 1차 개선 전](docs/assets/readme/community-1-before.png)

개선 후  
![커뮤니티 1차 개선 후](docs/assets/readme/community-1-after.png)

---

### 6️⃣ 커뮤니티 2차 성능 개선 - Redis 기반 카운터 시스템 구축

#### 문제 상황

- 조회수/좋아요 연산이 DB 중심으로 처리되어 커넥션/트랜잭션 부하 집중
- 단순 +1 연산도 DB 왕복이 필요해 트래픽 증가 시 병목 발생
- Race Condition 방지와 저지연 응답을 동시에 만족시키기 어려움

#### 해결 방법

- Redis를 실시간 카운터 처리 계층으로 분리
- Lua Script로 좋아요 토글/카운터 연산을 원자적으로 처리
- Event Queue 기반 좋아요 이벤트 적재 + Scheduler 배치 동기화(Write-Behind)
- Cache Miss 시 DB 초기 로딩(Cache-Aside) 적용

#### 결과

- DB 직접 갱신 빈도 감소
- 고빈도 카운터 연산의 처리 지연 감소
- 정합성/동시성/성능 균형을 갖춘 구조로 전환

---

### 7️⃣ 커뮤니티 3차 성능 개선 - DB 쿼리 최적화 및 캐싱 전략

#### 문제 상황

- 게시글 목록 조회 시 Redis N+1 호출 발생
- 페이지네이션 COUNT 쿼리 부하가 큼
- 인덱스 비효율로 Full Table Scan 발생

#### 해결 방법

- 복합 인덱스(`is_deleted`, `created_at DESC`) 추가
- 카운터 조회를 Bulk MGET으로 일괄 처리
- 캐시된 목록 + 최신 카운터 재주입 전략 적용
- 캐시 무효화 시 `KEYS` 대신 `SCAN` 사용

#### 결과

- Redis 호출 수 개선 : **30회/요청 → 3회/요청**
- 목록 조회 p95 개선
- DB 조회 부하 감소

---

## 🚀 트러블 슈팅

### 1️⃣ 카운터 정합성과 동시성을 유지하며 성능 향상의 어려움

#### 문제 상황

- 정합성(Consistency), 동시성(Concurrency), 성능(Performance)을 동시에 만족시키기 어려움
- 강한 락 기반 접근은 성능 저하, 단순 캐싱은 정합성 훼손 위험

#### 해결 방법

- 1차: Atomic Query로 데드락 제거
- 2차: Redis + Lua + Write-Behind로 실시간 처리와 정합성 동시 확보
- 3차: Bulk 조회 + 인덱스 + 캐시 전략으로 조회 경로 최적화

#### 결과

- 병목 구간을 단계적으로 분리해 해결
- 서비스 특성에 맞는 현실적인 트레이드오프 의사결정 경험 축적

### 2️⃣ 외부 결제 API 호출 제약 → Mock 기반 테스트 환경 구성

#### 문제 상황

- 실제 PG API는 성능 테스트/반복 호출에 제약이 존재
- 외부 API 의존도가 높아 테스트 재현성이 낮음

#### 해결 방법

- Mock Toss 결제 서비스 도입
- 프로필 기반으로 실제/Mock 결제 서비스 분리
- k6 시나리오를 Mock 기준으로 반복 실행 가능한 형태로 정리

#### 결과

- 결제 시나리오의 반복 검증 가능
- 외부 API 제약 없이 병목 구간 분석 가능
- 회귀 테스트 안정성 향상

#### 문제 상황

- 외부 서버에 1만 명 부하 테스트 중 요청의 80% 이상이 실패
- 로컬에서는 문제 없음 → 외부 서버에서만 연결 유실 발생
- Spring Actuator 활용한 분석 결과:
    - 서버가 요청 자체를 수신하지 못함
    - Tomcat의 accept() 호출 이전, OS 레벨 TCP Backlog Queue가 포화 상태

#### 원인 상세 분석

- Tomcat은 클라이언트 요청을 OS의 TCP 대기열(backlog)에 저장한 뒤 accept()를 통해 수락
- 이 때 accept-count 값이 낮으면 수락 대기열 공간이 부족해 연결 누락 발생
- 또한, OS의 somaxconn 값이 제한되어 있으면 Tomcat accept-count설정도 무의미해짐

#### 해결 방법

- Tomcat 설정 조정: accept-count를 10000으로 확장
- OS 튜닝: somaxconn 값을 10000으로 변경 → sysctl -w net.core.somaxconn=10000

#### 결과 및 인사이트

- 외부 부하 테스트에서 1만 개 요청 정상 수신
- TCP Backlog와 Tomcat accept-count 관계를 실전 환경에서 명확히 이해하고 적용

---

## 📖 Wiki 및 참고 자료

프로젝트 진행 중 겪었던 문제 해결 과정과 기술적 결정에 대한 자세한 내용은 아래 Wiki에서 확인할 수 있습니다.

### ⚙️ 1. 개선사항 및 트러블 슈팅

- 커뮤니티 1차 성능 개선 - 데드락 문제 해결
- 커뮤니티 2차 성능 개선 - Redis 기반 카운터 시스템 구축
- 커뮤니티 3차 성능 개선 - DB 쿼리 최적화 및 캐싱 전략
- 카운터 정합성과 동시성을 유지하며 성능 향상의 어려움

### 🗒️ 2. 기타 (Notes / Additional Info)

- CS스터디 스레드와 프로세스에 대하여
- CS스터디 Redis
- 글로벌 batchsize 미팅 제안서


- Wiki로 이동하기: [HomeSweetHome Wiki](https://github.com/ohhalim/HomeSweetHome-backend/wiki)

---

## 👥 Team

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
