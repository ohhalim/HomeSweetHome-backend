package com.homesweet.homesweetback.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 분산 락 AOP
 *
 * Redisson을 사용한 분산 락 구현
 *
 * @author HomeSweetHome Team
 * @date 2025-01-17
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();

        // SpEL 파싱하여 락 키 생성
        String lockKey = parseLockKey(
                distributedLock.key(),
                signature.getParameterNames(),
                joinPoint.getArgs()
        );

        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도
            boolean acquired = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!acquired) {
                log.warn("Failed to acquire lock for key: {} in method: {}", lockKey, methodName);
                throw new IllegalStateException("Could not acquire lock for key: " + lockKey);
            }

            log.debug("Lock acquired: {} for method: {}", lockKey, methodName);

            // 비즈니스 로직 실행
            return joinPoint.proceed();

        } finally {
            // 락 해제 (현재 스레드가 락을 보유한 경우에만)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: {} for method: {}", lockKey, methodName);
            }
        }
    }

    /**
     * SpEL을 사용하여 락 키 파싱
     */
    private String parseLockKey(String key, String[] parameterNames, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(key).getValue(context, String.class);
    }
}
