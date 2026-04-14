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

# 딥 페이징 범위 확대 (0~49 페이지)
k6 run -e COMMUNITY_LIST_PAGE_BOUND=50 k6/community-load-test.js

# 쓰기 시나리오 userId 상한 조정
# 주의: 게시글/댓글 작성은 실제 존재하는 userId만 사용해야 함
k6 run -e COMMUNITY_USER_ID_MAX=10 k6/community-load-test.js

# 간단 스모크 테스트 (VU 1명, 10초)
k6 run --vus 1 --duration 10s k6/community-load-test.js
```

### 주문/결제 부하테스트
```bash
# 기본 실행 (토큰 불필요, testUserId 파라미터 사용)
k6 run k6/order-payment-load-test.js

# 서버 주소 변경
k6 run -e BASE_URL=http://localhost:8080 k6/order-payment-load-test.js

# 단일 SKU 집중 타격
k6 run -e ORDER_HOT_SKU_ID=12345 k6/order-payment-load-test.js

# 단일 SKU 검증 포함 실행
k6 run -e ORDER_HOT_PRODUCT_ID=8801 -e ORDER_HOT_SKU_ID=12345 k6/order-payment-load-test.js

# 주문 생성만 집중 테스트 (order_read 비활성화)
k6 run \
  -e ORDER_CHECKOUT_WARMUP_VUS=60 \
  -e ORDER_CHECKOUT_PEAK_VUS=120 \
  -e ORDER_READ_WARMUP_VUS=0 \
  -e ORDER_READ_PEAK_VUS=0 \
  -e ORDER_HOT_SKU_ID=12345 \
  k6/order-payment-load-test.js

# 공격형 락 경합 테스트
# 상세 조회를 건너뛰고 sleep을 제거해 주문 생성/취소만 최대한 촘촘하게 반복
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ORDER_CHECKOUT_WARMUP_VUS=60 \
  -e ORDER_CHECKOUT_PEAK_VUS=120 \
  -e ORDER_READ_WARMUP_VUS=0 \
  -e ORDER_READ_PEAK_VUS=0 \
  -e ORDER_HOT_SKU_ID=12345 \
  -e ORDER_AGGRESSIVE_MODE=true \
  k6/order-payment-load-test.js

# 더 극단적인 테스트
# 주의: 취소까지 끄면 재고 소진이 락 경합보다 먼저 병목이 될 수 있음
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ORDER_CHECKOUT_WARMUP_VUS=60 \
  -e ORDER_CHECKOUT_PEAK_VUS=120 \
  -e ORDER_READ_WARMUP_VUS=0 \
  -e ORDER_READ_PEAK_VUS=0 \
  -e ORDER_HOT_SKU_ID=12345 \
  -e ORDER_AGGRESSIVE_MODE=true \
  -e ORDER_SKIP_CANCEL=true \
  k6/order-payment-load-test.js

# 간단 스모크 테스트
k6 run --vus 1 --duration 10s k6/order-payment-load-test.js
```

## 시나리오 구성

### community-load-test.js
| 시나리오 | 동시 유저 | 비율 | 내용 |
|---------|----------|------|------|
| read_heavy | 최대 50 VU | 80% | 게시글 목록/상세 조회, 조회수 증가, 댓글 조회 |
| write_flow | 최대 10 VU | 20% | 게시글 작성, 댓글 달기, 좋아요 토글 |

추가 옵션:
- `COMMUNITY_LIST_PAGE_BOUND`: 목록 조회 페이지 난수 범위 상한. `50`이면 `0~49` 페이지에서 조회합니다.
- `COMMUNITY_USER_ID_MAX`: `testUserId` 난수 상한. 쓰기 시나리오는 실제 존재하는 사용자 범위로만 늘리세요.

### order-payment-load-test.js
| 시나리오 | 동시 유저 | 내용 |
|---------|----------|------|
| checkout_flow | 최대 15 VU | 주문 생성 -> 주문 상세 조회 -> 주문 취소 |
| order_read | 최대 30 VU | 내 주문 목록 조회 |

추가 옵션:
- `ORDER_HOT_SKU_ID`: 모든 checkout 요청을 단일 SKU 하나에 집중시킵니다.
- `ORDER_HOT_PRODUCT_ID`: `ORDER_HOT_SKU_ID` 검증용 상품 ID입니다. 같이 주면 setup 단계에서 재고를 확인합니다.
- `ORDER_READ_WARMUP_VUS=0`, `ORDER_READ_PEAK_VUS=0`: `order_read` 시나리오를 완전히 비활성화합니다.
- `ORDER_AGGRESSIVE_MODE=true`: 기본 sleep을 제거하고 `ORDER_SKIP_DETAIL=true`로 바꿔 락 경합 탐지에 집중합니다.
- `ORDER_SKIP_DETAIL=true|false`: 주문 상세 조회 단계를 끕니다. 공격형 테스트에서는 기본값이 `true`입니다.
- `ORDER_SKIP_CANCEL=true|false`: 주문 취소 단계를 끕니다. 끄면 재고가 빠르게 소진될 수 있습니다.
- `ORDER_CREATE_TO_DETAIL_SLEEP_SEC`, `ORDER_DETAIL_TO_CANCEL_SLEEP_SEC`, `ORDER_CHECKOUT_LOOP_SLEEP_SEC`, `ORDER_READ_LOOP_SLEEP_SEC`, `ORDER_FAILURE_SLEEP_SEC`: 각 구간 sleep을 초 단위로 직접 제어합니다.

## 성능 기준 (thresholds)

| 메트릭 | 커뮤니티 | 주문/결제 |
|-------|---------|----------|
| p95 응답시간 | < 500ms | < 1000ms |
| p99 응답시간 | < 1000ms | < 2000ms |
| 에러율 | < 5% | < 10% |

## 인증 방식

모든 k6 테스트는 `testUserId` 쿼리 파라미터를 사용합니다.
JWT 토큰 없이 로컬에서 바로 실행 가능합니다.
