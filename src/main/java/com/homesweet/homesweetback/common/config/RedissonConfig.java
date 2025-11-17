package com.homesweet.homesweetback.common.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 분산 락 설정
 *
 * 사용처:
 * - 조회수 증가 동시성 제어
 * - 좋아요 토글 동시성 제어
 * - 재고 차감 등
 *
 * @author HomeSweetHome Team
 * @date 2025-01-17
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        String address = "redis://" + redisHost + ":" + redisPort;

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionPoolSize(64)          // 커넥션 풀 크기
                .setConnectionMinimumIdleSize(10)   // 최소 유휴 커넥션
                .setIdleConnectionTimeout(10000)    // 유휴 커넥션 타임아웃 (10초)
                .setConnectTimeout(10000)           // 연결 타임아웃 (10초)
                .setTimeout(3000)                   // 응답 타임아웃 (3초)
                .setRetryAttempts(3)                // 재시도 횟수
                .setRetryInterval(1500);            // 재시도 간격 (1.5초)

        RedissonClient redissonClient = Redisson.create(config);

        log.info("✓ Redisson Client initialized - Address: {}", address);
        return redissonClient;
    }
}
