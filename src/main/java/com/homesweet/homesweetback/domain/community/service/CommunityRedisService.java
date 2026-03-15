package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.domain.community.config.CommunityConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * [커뮤니티 Redis 서비스 - 조회수/댓글수 캐싱 전담]
 *
 * Redis에 조회수와 댓글수만 캐싱합니다.
 * 좋아요는 DB에서 직접 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CommunityConfig config;

    // ============================================================
    // [Lua 스크립트 - 원자적 연산 보장]
    // ============================================================

    /** 카운터 증가 스크립트: 키가 없으면 -1, 있으면 +1 */
    private static final String INCREMENT_COUNTER_SCRIPT =
            "if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end "
                    + "return redis.call('INCR', KEYS[1])";

    /** 카운터 업데이트 스크립트: 키가 없으면 -1, 있으면 인자값만큼 증감 */
    private static final String UPDATE_COUNTER_SCRIPT =
            "if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end "
                    + "return redis.call('INCRBY', KEYS[1], ARGV[1])";

    // ============================================================
    // [키 생성기]
    // ============================================================

    private String getPostViewKey(Long postId) {
        return "post:" + postId + ":viewCount";
    }

    private String getPostCommentCountKey(Long postId) {
        return "post:" + postId + ":commentCount";
    }

    // ============================================================
    // [TTL 헬퍼]
    // ============================================================

    private void expireWithDefaultTtl(String key) {
        long ttlSeconds = config.redis().ttl().toSeconds();
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    // ============================================================
    // [조회수 로직]
    // ============================================================

    public Long incrementPostViewCount(Long postId) {
        String key = getPostViewKey(postId);
        Long result = executeScript(INCREMENT_COUNTER_SCRIPT, Long.class, List.of(key));
        if (result != null && result != -1) {
            expireWithDefaultTtl(key);
        }
        return result;
    }

    public void setPostViewCount(Long postId, int count) {
        String key = getPostViewKey(postId);
        redisTemplate.opsForValue().set(key, count);
        expireWithDefaultTtl(key);
    }

    public Integer getPostViewCount(Long postId) {
        String key = getPostViewKey(postId);
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        if (count != null) {
            expireWithDefaultTtl(key);
        }
        return count;
    }

    // ============================================================
    // [댓글수 로직]
    // ============================================================

    public Long incrementPostCommentCount(Long postId) {
        String key = getPostCommentCountKey(postId);
        Long result = executeScript(INCREMENT_COUNTER_SCRIPT, Long.class, List.of(key));
        if (result != null && result != -1) {
            expireWithDefaultTtl(key);
        }
        return result;
    }

    public Long decreasePostCommentCount(Long postId) {
        String key = getPostCommentCountKey(postId);
        Long result = executeScript(UPDATE_COUNTER_SCRIPT, Long.class, List.of(key), "-1");
        if (result != null && result != -1) {
            expireWithDefaultTtl(key);
        }
        return result;
    }

    public void setPostCommentCount(Long postId, int count) {
        String key = getPostCommentCountKey(postId);
        redisTemplate.opsForValue().set(key, count);
        expireWithDefaultTtl(key);
    }

    public Integer getPostCommentCount(Long postId) {
        String key = getPostCommentCountKey(postId);
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        if (count != null) {
            expireWithDefaultTtl(key);
        }
        return count;
    }

    // ============================================================
    // [벌크 조회 - MGET 사용]
    // ============================================================

    public Map<Long, Integer> getBulkViewCounts(List<Long> postIds) {
        return getBulkCounts(postIds, this::getPostViewKey);
    }

    public Map<Long, Integer> getBulkCommentCounts(List<Long> postIds) {
        return getBulkCounts(postIds, this::getPostCommentCountKey);
    }

    private Map<Long, Integer> getBulkCounts(
            List<Long> ids, java.util.function.Function<Long, String> keyMapper) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<String> keys = ids.stream().map(keyMapper).toList();
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            Object value = values != null ? values.get(i) : null;
            result.put(ids.get(i), value != null ? (Integer) value : null);
        }
        return result;
    }

    // ============================================================
    // [스케줄러용 SCAN]
    // ============================================================

    public Map<Long, Integer> scanAndCollectViewCounts() {
        return scanAndCollect("post:*:viewCount", this::extractPostIdFromKey, this::getPostViewCount);
    }

    public Map<Long, Integer> scanAndCollectCommentCounts() {
        return scanAndCollect(
                "post:*:commentCount", this::extractPostIdFromKey, this::getPostCommentCount);
    }

    private Map<Long, Integer> scanAndCollect(
            String pattern,
            java.util.function.Function<String, Long> idExtractor,
            java.util.function.Function<Long, Integer> countGetter) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        Map<Long, Integer> result = new HashMap<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    Long id = idExtractor.apply(key);
                    Integer count = countGetter.apply(id);
                    if (count != null) {
                        result.put(id, count);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse Redis key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan Redis for pattern: {}", pattern, e);
        }
        return result;
    }

    // ============================================================
    // [키 삭제]
    // ============================================================

    public void deletePostViewKey(Long postId) {
        redisTemplate.delete(getPostViewKey(postId));
    }

    public void deletePostCommentCountKey(Long postId) {
        redisTemplate.delete(getPostCommentCountKey(postId));
    }

    // ============================================================
    // [내부 헬퍼]
    // ============================================================

    private <T> T executeScript(
            String scriptText, Class<T> returnType, List<String> keys, Object... args) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>(scriptText, returnType);
        return redisTemplate.execute(script, keys, args);
    }

    private Long extractPostIdFromKey(String key) {
        String[] parts = key.split(":");
        return Long.parseLong(parts[1]);
    }
}