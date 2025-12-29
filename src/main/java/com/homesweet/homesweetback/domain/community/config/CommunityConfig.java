package com.homesweet.homesweetback.domain.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Community 도메인 설정
 * application.yml의 community.* 설정을 바인딩
 */
@ConfigurationProperties(prefix = "community")
public record CommunityConfig(
        CacheConfig cache,
        SchedulerConfig scheduler,
        RedisConfig redis) {
    /**
     * 서비스 레이어 캐시 설정
     */
    public record CacheConfig(
            Duration postTtl, // 게시글 단건 캐시 TTL (기본: 1시간)
            Duration listTtl, // 게시글 목록 캐시 TTL (기본: 1분)
            Duration commentsTtl // 댓글 목록 캐시 TTL (기본: 30분)
    ) {
        public CacheConfig {
            if (postTtl == null)
                postTtl = Duration.ofHours(1);
            if (listTtl == null)
                listTtl = Duration.ofMinutes(1);
            if (commentsTtl == null)
                commentsTtl = Duration.ofMinutes(30);
        }
    }

    /**
     * 스케줄러 배치 동기화 설정 (밀리초)
     */
    public record SchedulerConfig(
            long viewSyncDelay, // 조회수 동기화 주기 (기본: 110초)
            long commentSyncDelay, // 댓글수 동기화 주기 (기본: 210초)
            long likeSyncDelay, // 좋아요 동기화 주기 (기본: 300초)
            long warmupDelay // 캐시 워밍업 주기 (기본: 1시간)
    ) {
        public SchedulerConfig {
            if (viewSyncDelay <= 0)
                viewSyncDelay = 110000;
            if (commentSyncDelay <= 0)
                commentSyncDelay = 210000;
            if (likeSyncDelay <= 0)
                likeSyncDelay = 300000;
            if (warmupDelay <= 0)
                warmupDelay = 3600000;
        }
    }

    /**
     * Redis 카운터 TTL 설정
     */
    public record RedisConfig(
            Duration ttl // Redis 카운터 TTL (기본: 7일)
    ) {
        public RedisConfig {
            if (ttl == null)
                ttl = Duration.ofDays(7);
        }
    }

    /**
     * 기본값 설정
     */
    public CommunityConfig {
        if (cache == null)
            cache = new CacheConfig(null, null, null);
        if (scheduler == null)
            scheduler = new SchedulerConfig(0, 0, 0, 0);
        if (redis == null)
            redis = new RedisConfig(null);
    }
}
