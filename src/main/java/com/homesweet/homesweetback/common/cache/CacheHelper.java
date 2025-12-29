package com.homesweet.homesweetback.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 캐시 조회/저장 추상화 유틸리티
 * JSON 직렬화/역직렬화 로직 통합
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheHelper {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 캐시에서 값 조회 (단일 타입)
     */
    public <T> Optional<T> getFromCache(String key, Class<T> type) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            log.warn("Cache deserialization failed for key: {}", key, e);
            stringRedisTemplate.delete(key);
            return Optional.empty();
        }
    }

    /**
     * 캐시에서 값 조회 (제네릭 타입 - List, Map 등)
     */
    public <T> Optional<T> getFromCache(String key, TypeReference<T> typeRef) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, typeRef));
        } catch (JsonProcessingException e) {
            log.warn("Cache deserialization failed for key: {}", key, e);
            stringRedisTemplate.delete(key);
            return Optional.empty();
        }
    }

    /**
     * 캐시에 값 저장
     */
    public <T> void setCache(String key, T value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, ttl);
            log.debug("Cached value for key: {}", key);
        } catch (JsonProcessingException e) {
            log.warn("Cache serialization failed for key: {}", key, e);
        }
    }

    /**
     * 캐시 삭제
     */
    public void deleteCache(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 패턴 매칭으로 캐시 무효화 (SCAN 사용 - 프로덕션 안전)
     */
    public int invalidateCacheByPattern(String pattern) {
        var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();

        int deletedCount = 0;
        try (var cursor = stringRedisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                stringRedisTemplate.delete(key);
                deletedCount++;
            }
        }

        if (deletedCount > 0) {
            log.debug("Invalidated {} cache entries matching pattern: {}", deletedCount, pattern);
        }
        return deletedCount;
    }
}
