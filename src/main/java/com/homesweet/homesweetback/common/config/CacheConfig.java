package com.homesweet.homesweetback.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 다단계 캐시 설정
 *
 * L1 Cache: Caffeine (Local, In-Memory)
 * L2 Cache: Redis (Distributed)
 *
 * @author HomeSweetHome Team
 * @date 2025-01-17
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * L1 Cache: Caffeine (Local Cache)
     *
     * 특징:
     * - 메모리 내 저장 (매우 빠름)
     * - 애플리케이션 인스턴스마다 독립적
     * - 작은 데이터, 자주 조회되는 데이터에 적합
     */
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                // 캐시 이름 정의
                "communityPostCache",           // 게시글 상세
                "communityPostListCache",       // 게시글 목록
                "communityCommentCache",        // 댓글 목록
                "communityUserStatsCache"       // 사용자 통계
        );

        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(10_000)                    // 최대 10,000개 엔트리
                        .expireAfterWrite(5, TimeUnit.MINUTES)  // 5분 후 만료
                        .expireAfterAccess(3, TimeUnit.MINUTES) // 3분간 미사용 시 만료
                        .recordStats()                          // 통계 기록
        );

        log.info("✓ Caffeine L1 Cache Manager initialized");
        return cacheManager;
    }

    /**
     * L2 Cache: Redis (Distributed Cache)
     *
     * 특징:
     * - 분산 환경에서 공유
     * - 영구 저장 가능
     * - 큰 데이터, 여러 서버에서 공유해야 하는 데이터에 적합
     */
    @Bean
    public CacheManager redisCacheManager() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper)
                        )
                )
                .entryTtl(Duration.ofMinutes(10))  // 기본 10분
                .disableCachingNullValues();

        // 캐시별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 게시글 상세 - 30분
        cacheConfigurations.put("communityPost",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 게시글 목록 - 5분 (자주 변경됨)
        cacheConfigurations.put("communityPostList",
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 댓글 목록 - 10분
        cacheConfigurations.put("communityComments",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // 트렌딩 게시글 - 1시간
        cacheConfigurations.put("trendingPosts",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        // 추천 게시글 - 1시간
        cacheConfigurations.put("recommendedPosts",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();

        log.info("✓ Redis L2 Cache Manager initialized");
        return cacheManager;
    }

    /**
     * RedisTemplate 설정
     *
     * 캐시 매니저가 아닌 직접 Redis 조작이 필요한 경우 사용
     * - 조회수 카운터
     * - 좋아요 카운터
     * - 랭킹
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Key Serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value Serializer
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.info("✓ RedisTemplate initialized");
        return template;
    }

    /**
     * String 전용 RedisTemplate
     *
     * 간단한 String 값 저장에 사용
     * - 카운터
     * - 플래그
     */
    @Bean
    public RedisTemplate<String, String> stringRedisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();

        log.info("✓ String RedisTemplate initialized");
        return template;
    }
}
