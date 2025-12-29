package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.domain.community.config.CommunityConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final CommunityConfig config;

    // --- Lua Scripts ---
    private static final String TOGGLE_LIKE_SCRIPT = "if redis.call('EXISTS', KEYS[2]) == 0 then " +
            "  return -1 " +
            "end " +
            "local isMember = redis.call('SISMEMBER', KEYS[1], ARGV[1]) " +
            "if isMember == 1 then " +
            "  redis.call('SREM', KEYS[1], ARGV[1]) " +
            "  redis.call('DECR', KEYS[2]) " +
            "  return 0 " +
            "else " +
            "  redis.call('SADD', KEYS[1], ARGV[1]) " +
            "  redis.call('INCR', KEYS[2]) " +
            "  return 1 " +
            "end";

    private static final String INCREMENT_COUNTER_SCRIPT = "if redis.call('EXISTS', KEYS[1]) == 0 then " +
            "  return -1 " +
            "end " +
            "return redis.call('INCR', KEYS[1])";

    private static final String UPDATE_COUNTER_SCRIPT = "if redis.call('EXISTS', KEYS[1]) == 0 then " +
            "  return -1 " +
            "end " +
            "return redis.call('INCRBY', KEYS[1], ARGV[1])";

    // --- Key Generators ---
    private String getPostViewKey(Long postId) {
        return "post:" + postId + ":viewCount";
    }

    private String getPostCommentCountKey(Long postId) {
        return "post:" + postId + ":commentCount";
    }

    private String getPostLikeSetKey(Long postId) {
        return "post:" + postId + ":likes";
    }

    private String getPostLikeCountKey(Long postId) {
        return "post:" + postId + ":likeCount";
    }

    private String getCommentLikeSetKey(Long commentId) {
        return "comment:" + commentId + ":likes";
    }

    private String getCommentLikeCountKey(Long commentId) {
        return "comment:" + commentId + ":likeCount";
    }

    private static final String POST_LIKE_EVENT_QUEUE = "post:like:events";
    private static final String COMMENT_LIKE_EVENT_QUEUE = "comment:like:events";

    // ============================================================
    // TTL Helper
    // ============================================================

    private void expireWithDefaultTtl(String key) {
        long ttlSeconds = config.redis().ttl().toSeconds();
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    private void expireWithDefaultTtl(String... keys) {
        long ttlSeconds = config.redis().ttl().toSeconds();
        for (String key : keys) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    // ============================================================
    // View Count Logic
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
    // Comment Count Logic
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
    // Post Like Logic
    // ============================================================

    public Long togglePostLike(Long postId, Long userId) {
        String likeSetKey = getPostLikeSetKey(postId);
        String countKey = getPostLikeCountKey(postId);

        Long result = executeScript(TOGGLE_LIKE_SCRIPT, Long.class,
                Arrays.asList(likeSetKey, countKey), userId.toString());

        if (result != null && result != -1) {
            expireWithDefaultTtl(likeSetKey, countKey);
        }
        return result;
    }

    public void setPostLikes(Long postId, List<Long> userIds) {
        String likeSetKey = getPostLikeSetKey(postId);
        String countKey = getPostLikeCountKey(postId);

        if (!userIds.isEmpty()) {
            String[] userIdStrings = userIds.stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.opsForSet().add(likeSetKey, (Object[]) userIdStrings);
        }
        redisTemplate.opsForValue().set(countKey, userIds.size());
        expireWithDefaultTtl(likeSetKey, countKey);
    }

    public boolean isPostLiked(Long postId, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(getPostLikeSetKey(postId), userId.toString()));
    }

    public boolean hasPostLikeKey(Long postId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(getPostLikeSetKey(postId)));
    }

    public Integer getPostLikeCount(Long postId) {
        String key = getPostLikeCountKey(postId);
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        if (count != null) {
            expireWithDefaultTtl(key, getPostLikeSetKey(postId));
        }
        return count;
    }

    public void addPostLikeEvent(Long postId, Long userId, boolean isAdded) {
        String event = postId + ":" + userId + ":" + (isAdded ? "ADD" : "REMOVE");
        stringRedisTemplate.opsForList().rightPush(POST_LIKE_EVENT_QUEUE, event);
    }

    // ============================================================
    // Comment Like Logic
    // ============================================================

    public Long toggleCommentLike(Long commentId, Long userId) {
        String likeSetKey = getCommentLikeSetKey(commentId);
        String countKey = getCommentLikeCountKey(commentId);

        Long result = executeScript(TOGGLE_LIKE_SCRIPT, Long.class,
                Arrays.asList(likeSetKey, countKey), userId.toString());

        if (result != null && result != -1) {
            expireWithDefaultTtl(likeSetKey, countKey);
        }
        return result;
    }

    public void setCommentLikes(Long commentId, List<Long> userIds) {
        String likeSetKey = getCommentLikeSetKey(commentId);
        String countKey = getCommentLikeCountKey(commentId);

        if (!userIds.isEmpty()) {
            String[] userIdStrings = userIds.stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.opsForSet().add(likeSetKey, (Object[]) userIdStrings);
        }
        redisTemplate.opsForValue().set(countKey, userIds.size());
        expireWithDefaultTtl(likeSetKey, countKey);
    }

    public boolean isCommentLiked(Long commentId, Long userId) {
        return Boolean.TRUE
                .equals(redisTemplate.opsForSet().isMember(getCommentLikeSetKey(commentId), userId.toString()));
    }

    public boolean hasCommentLikeKey(Long commentId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(getCommentLikeSetKey(commentId)));
    }

    public Integer getCommentLikeCount(Long commentId) {
        String key = getCommentLikeCountKey(commentId);
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        if (count != null) {
            expireWithDefaultTtl(key, getCommentLikeSetKey(commentId));
        }
        return count;
    }

    public void addCommentLikeEvent(Long commentId, Long userId, boolean isAdded) {
        String event = commentId + ":" + userId + ":" + (isAdded ? "ADD" : "REMOVE");
        stringRedisTemplate.opsForList().rightPush(COMMENT_LIKE_EVENT_QUEUE, event);
    }

    // ============================================================
    // Bulk Operations (for Post List - MGET)
    // ============================================================

    public Map<Long, Integer> getBulkViewCounts(List<Long> postIds) {
        return getBulkCounts(postIds, this::getPostViewKey);
    }

    public Map<Long, Integer> getBulkLikeCounts(List<Long> postIds) {
        return getBulkCounts(postIds, this::getPostLikeCountKey);
    }

    public Map<Long, Integer> getBulkCommentCounts(List<Long> postIds) {
        return getBulkCounts(postIds, this::getPostCommentCountKey);
    }

    /**
     * 여러 댓글의 좋아요수를 한 번에 조회 (MGET)
     */
    public Map<Long, Integer> getBulkCommentLikeCounts(List<Long> commentIds) {
        return getBulkCounts(commentIds, this::getCommentLikeCountKey);
    }

    private Map<Long, Integer> getBulkCounts(List<Long> ids, java.util.function.Function<Long, String> keyMapper) {
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
    // Batch Operations (for Scheduler)
    // ============================================================

    public Map<Long, Integer> scanAndCollectViewCounts() {
        return scanAndCollect("post:*:viewCount", this::extractPostIdFromKey, this::getPostViewCount);
    }

    public Map<Long, Integer> scanAndCollectCommentCounts() {
        return scanAndCollect("post:*:commentCount", this::extractPostIdFromKey, this::getPostCommentCount);
    }

    public Map<Long, Integer> scanAndCollectPostLikeCounts() {
        return scanAndCollect("post:*:likeCount", this::extractPostIdFromKey, this::getPostLikeCount);
    }

    public Map<Long, Integer> scanAndCollectCommentLikeCounts() {
        return scanAndCollect("comment:*:likeCount", this::extractCommentIdFromKey, this::getCommentLikeCount);
    }

    private Map<Long, Integer> scanAndCollect(String pattern,
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
    // Event Queue Operations
    // ============================================================

    public List<LikeEvent> pollPostLikeEvents(int maxSize) {
        return pollLikeEvents(POST_LIKE_EVENT_QUEUE, maxSize);
    }

    public List<LikeEvent> pollCommentLikeEvents(int maxSize) {
        return pollLikeEvents(COMMENT_LIKE_EVENT_QUEUE, maxSize);
    }

    private List<LikeEvent> pollLikeEvents(String queueKey, int maxSize) {
        List<String> events = stringRedisTemplate.opsForList().range(queueKey, 0, maxSize - 1);
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .map(this::parseLikeEvent)
                .filter(Objects::nonNull)
                .toList();
    }

    public void trimPostLikeEvents(int processedCount) {
        stringRedisTemplate.opsForList().trim(POST_LIKE_EVENT_QUEUE, processedCount, -1);
    }

    public void trimCommentLikeEvents(int processedCount) {
        stringRedisTemplate.opsForList().trim(COMMENT_LIKE_EVENT_QUEUE, processedCount, -1);
    }

    // ============================================================
    // Key Deletion
    // ============================================================

    public void deletePostViewKey(Long postId) {
        redisTemplate.delete(getPostViewKey(postId));
    }

    public void deletePostCommentCountKey(Long postId) {
        redisTemplate.delete(getPostCommentCountKey(postId));
    }

    public void deletePostLikeKeys(Long postId) {
        redisTemplate.delete(getPostLikeSetKey(postId));
        redisTemplate.delete(getPostLikeCountKey(postId));
    }

    public void deleteCommentLikeKeys(Long commentId) {
        redisTemplate.delete(getCommentLikeSetKey(commentId));
        redisTemplate.delete(getCommentLikeCountKey(commentId));
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================

    private <T> T executeScript(String scriptText, Class<T> returnType, List<String> keys, Object... args) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>(scriptText, returnType);
        return redisTemplate.execute(script, keys, args);
    }

    private Long extractPostIdFromKey(String key) {
        String[] parts = key.split(":");
        return Long.parseLong(parts[1]);
    }

    private Long extractCommentIdFromKey(String key) {
        String[] parts = key.split(":");
        return Long.parseLong(parts[1]);
    }

    private LikeEvent parseLikeEvent(String event) {
        try {
            String[] parts = event.split(":");
            if (parts.length == 3) {
                return new LikeEvent(
                        Long.parseLong(parts[0]),
                        Long.parseLong(parts[1]),
                        "ADD".equals(parts[2]));
            }
        } catch (Exception e) {
            log.error("Failed to parse like event: {}", event, e);
        }
        return null;
    }

    /**
     * 좋아요 이벤트 DTO
     */
    public record LikeEvent(Long targetId, Long userId, boolean isAdd) {
    }
}