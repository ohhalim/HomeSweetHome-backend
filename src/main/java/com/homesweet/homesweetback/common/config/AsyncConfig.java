package com.homesweet.homesweetback.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * 
 * 알림 서비스의 비동기 처리를 위한 스레드 풀 설정
 * 
 * @author dogyungkim
 */
@Slf4j
@Configuration
@EnableAsync
@Profile("!test")
public class AsyncConfig {

    /**
     * 알림 전용 스레드 풀
     * 
     * - corePoolSize: 기본 스레드 수 (5개)
     * - maxPoolSize: 최대 스레드 수 (10개)
     * - queueCapacity: 대기 큐 크기 (100개)
     * - threadNamePrefix: 스레드 이름 접두사
     */
    @Bean(name = "notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("알림 작업이 거부되었습니다. 큐가 가득 찼습니다.");
        });
        executor.initialize();
        return executor;
    }

    // 상품 검색 이벤트 발행 비동기 처리
    @Bean(name = "productEventExecutor")
    public Executor productEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("product-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("상품 이벤트 비동기 처리에 실패하였습니다.");
        });
        executor.initialize();
        return executor;
    }

    // 게시글 검색 이벤트 발행 비동기 처리
    @Bean(name = "communityEventExecutor")
    public Executor communityEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("community-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("게시글 엘라스틱 이벤트 비동기 처리에 실패하였습니다.");
        });
        executor.initialize();
        return executor;
    }

    // 채팅방 검색 이벤트 발행 비동기 처리
    @Bean(name = "chatroomEventExecutor")
    public Executor chatroomEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chatroom-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("채팅방 엘라스틱 이벤트 비동기 처리에 실패하였습니다.");
        });
        executor.initialize();
        return executor;
    }

    /**
     * 재고 DB 동기화 전용 스레드 풀
     *
     * AFTER_COMMIT 리스너를 비동기로 실행해 요청 스레드의 HikariCP 커넥션을
     * 먼저 반환한 뒤 별도 스레드에서 DB sync를 처리한다.
     * REQUIRES_NEW를 요청 스레드에서 쓰면 커넥션을 2개 잡아 풀 고갈이 발생한다.
     */
    @Bean(name = "stockSyncExecutor")
    public Executor stockSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("stock-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.error("[STOCK-SYNC] stockSyncExecutor 큐 포화 — DB sync 유실. reconciliation 필요.");
        });
        executor.initialize();
        return executor;
    }
}
