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

    @Bean(name = "recentSearchTaskExecutor")
    public Executor recentSearchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("recent-search-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("최근 검색어 저장 작업이 거부되었습니다. 큐가 가득 찼습니다.");
        });
        executor.initialize();
        return executor;
    }

    /**
     * 커뮤니티 이벤트 처리용 스레드 풀
     */
    @Bean(name = "communityEventExecutor")
    public Executor communityEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("community-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("커뮤니티 이벤트 처리 작업이 거부되었습니다. 큐가 가득 찼습니다.");
        });
        executor.initialize();
        return executor;
    }

    /**
     * 이미지 업로드용 스레드 풀
     */
    @Bean(name = "imageUploadExecutor")
    public Executor imageUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("image-upload-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("이미지 업로드 작업이 거부되었습니다. 큐가 가득 찼습니다.");
        });
        executor.initialize();
        return executor;
    }
}

