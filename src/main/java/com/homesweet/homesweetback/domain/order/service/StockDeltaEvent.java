package com.homesweet.homesweetback.domain.order.service;

/**
 * 재고 변경 이벤트
 *
 * 주문 생성/취소 트랜잭션 내에서 발행되며,
 * AFTER_COMMIT → DB 재고 동기화
 * AFTER_ROLLBACK → Redis 재고 보상 (DECREASE 실패 시)
 * 에 사용된다.
 */
public record StockDeltaEvent(Long skuId, long quantity, Type type) {

    public enum Type {
        DECREASE,
        INCREASE
    }

    public static StockDeltaEvent decrease(Long skuId, long quantity) {
        return new StockDeltaEvent(skuId, quantity, Type.DECREASE);
    }

    public static StockDeltaEvent increase(Long skuId, long quantity) {
        return new StockDeltaEvent(skuId, quantity, Type.INCREASE);
    }
}
