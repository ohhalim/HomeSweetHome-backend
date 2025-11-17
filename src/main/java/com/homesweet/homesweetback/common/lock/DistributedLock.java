package com.homesweet.homesweetback.common.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산 락 어노테이션
 *
 * 사용 예:
 * {@code
 * @DistributedLock(key = "'post:' + #postId + ':views'", waitTime = 5, leaseTime = 3)
 * public void increaseViews(Long postId) {
 *     // 동시성 제어가 필요한 로직
 * }
 * }
 *
 * @author HomeSweetHome Team
 * @date 2025-01-17
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락 키 (SpEL 지원)
     * 예: "'post:' + #postId + ':views'"
     */
    String key();

    /**
     * 락 획득 대기 시간 (초)
     * 기본값: 5초
     */
    long waitTime() default 5L;

    /**
     * 락 유지 시간 (초)
     * 기본값: 3초
     */
    long leaseTime() default 3L;

    /**
     * 시간 단위
     * 기본값: 초
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
