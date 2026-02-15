package com.homesweet.homesweetback.domain.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRedisGuardService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "payment:idempotency:";
    private static final String ORDER_LOCK_KEY_PREFIX = "payment:lock:order:";

    private static final Duration IDEMPOTENCY_TTL = Duration.ofSeconds(30);
    private static final Duration ORDER_LOCK_TTL = Duration.ofSeconds(30);

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT;

    static {
        RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>();
        RELEASE_LOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end");
        RELEASE_LOCK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryAcquireIdempotency(String paymentKey) {
        String key = idempotencyKey(paymentKey);
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, "IN_PROGRESS", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public void markIdempotencyCompleted(String paymentKey) {
        stringRedisTemplate.opsForValue().set(idempotencyKey(paymentKey), "DONE", IDEMPOTENCY_TTL);
    }

    public void clearIdempotency(String paymentKey) {
        stringRedisTemplate.delete(idempotencyKey(paymentKey));
    }

    public String tryAcquireOrderLock(String orderId) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(orderLockKey(orderId), token, ORDER_LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return token;
        }
        return null;
    }

    public void releaseOrderLock(String orderId, String token) {
        if (token == null) {
            return;
        }

        try {
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(orderLockKey(orderId)), token);
        } catch (Exception e) {
            log.warn("Failed to release payment order lock. orderId={}", orderId, e);
        }
    }

    private String idempotencyKey(String paymentKey) {
        return IDEMPOTENCY_KEY_PREFIX + paymentKey;
    }

    private String orderLockKey(String orderId) {
        return ORDER_LOCK_KEY_PREFIX + orderId;
    }
}
