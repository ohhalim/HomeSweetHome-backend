# k6 부하테스트

## 사전 준비

```bash
# k6 설치 (Mac)
brew install k6

# 백엔드 서버 실행
./gradlew bootRun

# Redis 실행 (커뮤니티 테스트에 필요)
docker compose -f docker-compose.dev.yml up -d redis
```

## 실행 방법

### 커뮤니티 부하테스트
```bash
# 기본 실행 (localhost:8080)
k6 run k6/community-load-test.js

# 서버 주소 변경
k6 run -e BASE_URL=http://localhost:8080 k6/community-load-test.js

# 간단 스모크 테스트 (VU 1명, 10초)
k6 run --vus 1 --duration 10s k6/community-load-test.js
```

### 주문/결제 부하테스트
```bash
# JWT 토큰 포함 실행
k6 run -e AUTH_TOKEN=<your_jwt_token> k6/order-payment-load-test.js

# 토큰 없이 실행 (401 에러 예상, 서버 응답 확인용)
k6 run k6/order-payment-load-test.js
```

## 시나리오 구성

### community-load-test.js
| 시나리오 | 동시 유저 | 비율 | 내용 |
|---------|----------|------|------|
| read_heavy | 최대 50 VU | 80% | 게시글 목록/상세 조회, 조회수 증가, 댓글 조회 |
| write_flow | 최대 10 VU | 20% | 게시글 작성, 댓글 달기, 좋아요 토글 |

### order-payment-load-test.js
| 시나리오 | 동시 유저 | 내용 |
|---------|----------|------|
| checkout_flow | 최대 15 VU | 주문 생성 -> 결제 승인 -> 상태 확인 풀 플로우 |
| order_read | 최대 30 VU | 내 주문 목록 조회 |

## 성능 기준 (thresholds)

| 메트릭 | 커뮤니티 | 주문/결제 |
|-------|---------|----------|
| p95 응답시간 | < 500ms | < 1000ms |
| p99 응답시간 | < 1000ms | < 2000ms |
| 에러율 | < 5% | < 10% |
