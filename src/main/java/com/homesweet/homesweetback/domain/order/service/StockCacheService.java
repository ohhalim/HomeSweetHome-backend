package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.common.exception.StockInsufficientException;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 기반 재고 캐시 서비스
 *
 * DB row lock 직렬화 병목 제거를 위해 재고 차감/복원을 Redis 원자 연산으로 처리한다.
 * key 형식: stock:sku:{skuId}
 *
 * 흐름:
 *   주문 생성 → Redis DECRBY (원자적 차감, ~1ms) → DB UPDATE (lock 없는 단순 기록)
 *   주문 취소 → Redis INCRBY → DB UPDATE
 *   초기화   → Redis key 없으면 DB에서 lazy load
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheService {

    private static final String STOCK_KEY_PREFIX = "stock:sku:";

    private final StringRedisTemplate stringRedisTemplate;
    private final SkuJPARepository skuJPARepository;

    /**
     * Redis 원자적 재고 차감.
     * 잔여 재고가 음수가 되면 즉시 롤백하고 예외를 던진다.
     *
     * @return 차감 후 잔여 재고
     * @throws StockInsufficientException 재고 부족
     */
    public long decrease(Long skuId, long quantity) {
        ensureInitialized(skuId);
        String key = stockKey(skuId);

        Long remaining = stringRedisTemplate.opsForValue().decrement(key, quantity);
        if (remaining == null || remaining < 0) {
            // 음수 방지: 즉시 되돌린다
            stringRedisTemplate.opsForValue().increment(key, quantity);
            log.warn("[STOCK-CACHE] 재고 부족 skuId={}, qty={}", skuId, quantity);
            throw new StockInsufficientException("재고가 부족합니다. (SKU: " + skuId + ", 요청 수량: " + quantity + ")");
        }
        log.debug("[STOCK-CACHE] decrease skuId={}, qty={}, remaining={}", skuId, quantity, remaining);
        return remaining;
    }

    /**
     * Redis 원자적 재고 복원 (주문 취소 or 롤백).
     */
    public void restore(Long skuId, long quantity) {
        ensureInitialized(skuId);
        Long after = stringRedisTemplate.opsForValue().increment(stockKey(skuId), quantity);
        log.debug("[STOCK-CACHE] restore skuId={}, qty={}, after={}", skuId, quantity, after);
    }

    /**
     * Redis key가 없으면 DB에서 현재 재고를 읽어 초기화한다 (lazy init).
     * 동시 요청 시 setIfAbsent(NX)로 중복 초기화를 방지한다.
     */
    private void ensureInitialized(Long skuId) {
        String key = stockKey(skuId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        skuJPARepository.findById(skuId).ifPresent(sku -> {
            Boolean set = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(sku.getStockQuantity()));
            if (Boolean.TRUE.equals(set)) {
                log.info("[STOCK-CACHE] initialized skuId={}, stock={}", skuId, sku.getStockQuantity());
            }
        });
    }

    private String stockKey(Long skuId) {
        return STOCK_KEY_PREFIX + skuId;
    }
}
