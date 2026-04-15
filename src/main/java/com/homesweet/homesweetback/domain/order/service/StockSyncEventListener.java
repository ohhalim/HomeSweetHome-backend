package com.homesweet.homesweetback.domain.order.service;

import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 재고 변경 이벤트 리스너
 *
 * AFTER_COMMIT: DB 재고 동기화 (REQUIRES_NEW — 메인 TX와 별도 짧은 트랜잭션)
 * AFTER_ROLLBACK: Redis 재고 보상 — 주문 생성 TX 롤백 시 Redis에 차감된 재고를 복원
 *
 * DB sync 실패 시 예외를 삼켜 HTTP 500을 방지한다.
 * 이미 커밋된 주문에 500을 돌려주는 것보다 로그 + reconciliation이 낫다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSyncEventListener {

    private final SkuJPARepository skuJPARepository;
    private final StockCacheService stockCacheService;

    @Async("stockSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCommit(StockDeltaEvent event) {
        try {
            if (event.type() == StockDeltaEvent.Type.DECREASE) {
                skuJPARepository.decreaseStockDirect(event.skuId(), event.quantity());
            } else {
                skuJPARepository.increaseStock(event.skuId(), event.quantity());
            }
            log.info("[STOCK-SYNC] DB synced skuId={}, qty={}, type={}",
                    event.skuId(), event.quantity(), event.type());
        } catch (Exception e) {
            // 주문은 이미 커밋 — 500 던지지 않고 로그만 남긴다.
            // reconciliation 스케줄러가 나중에 Redis ↔ DB 불일치를 잡아준다.
            log.error("[STOCK-SYNC] DB sync failed — needs reconciliation. skuId={}, qty={}, type={}",
                    event.skuId(), event.quantity(), event.type(), e);
        }
    }

    /**
     * 주문 생성 TX가 롤백된 경우 Redis에서 차감된 재고를 복원한다.
     * INCREASE(취소) 롤백은 reconciliation에 위임한다 (취소 TX 자체가 롤백되는 케이스는 매우 드물고
     * 반대 방향 decrease를 여기서 쏘면 ensureInitialized 등 side-effect가 생길 수 있다).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onRollback(StockDeltaEvent event) {
        if (event.type() != StockDeltaEvent.Type.DECREASE) {
            log.warn("[STOCK-SYNC] INCREASE event rolled back — reconciliation needed. skuId={}, qty={}",
                    event.skuId(), event.quantity());
            return;
        }
        try {
            stockCacheService.restore(event.skuId(), event.quantity());
            log.warn("[STOCK-SYNC] TX rolled back, Redis restored. skuId={}, qty={}",
                    event.skuId(), event.quantity());
        } catch (Exception e) {
            log.error("[STOCK-SYNC] Redis restore failed after rollback. skuId={}, qty={}",
                    event.skuId(), event.quantity(), e);
        }
    }
}
