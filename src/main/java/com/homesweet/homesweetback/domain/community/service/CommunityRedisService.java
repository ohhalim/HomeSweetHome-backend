package com.homesweet.homesweetback.domain.community.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    // --- Lua Scripts ---
    private static final String TOGGLE_LIKE_SCRIPT =
            "if redis.call('EXISTS', KEYS[2]) == 0 then " +
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

    private static final String INCREMENT_COUNTER_SCRIPT =
            "if redis.call('EXISTS', KEYS[1]) == 0 then " +
                    "  return -1 " +
                    "end " +
                    "return redis.call('INCR', KEYS[1])";

    private static final String UPDATE_COUNTER_SCRIPT =
            "if redis.call('EXISTS', KEYS[1]) == 0 then " +
                    "  return -1 " +
                    "end " +
                    "return redis.call('INCRBY', KEYS[1], ARGV[1])";

    // --- Key Generators ---
    private String getPostViewKey(Long postId) { return "post:" + postId + ":viewCount"; }
    private String getPostCommentCountKey(Long postId) { return "post:" + postId + ":commentCount"; }
    private String getPostLikeSetKey(Long postId) { return "post:" + postId + ":likes"; }
    private String getPostLikeCountKey(Long postId) { return "post:" + postId + ":likeCount"; }

    private String getCommentLikeSetKey(Long commentId) { return "comment:" + commentId + ":likes"; }
    private String getCommentLikeCountKey(Long commentId) { return "comment:" + commentId + ":likeCount"; }

    private static final String POST_LIKE_EVENT_QUEUE = "post:like:events";
    private static final String COMMENT_LIKE_EVENT_QUEUE = "comment:like:events";

    // --- View Count Logic ---
    public Long incrementPostViewCount(Long postId) {
        String key = getPostViewKey(postId);
        Long result = executeScript(INCREMENT_COUNTER_SCRIPT, Long.class, List.of(key));

        // 접근 시 TTL 연장 (7일)
        if (result != null && result != -1) {
            redisTemplate.expire(key, 7, TimeUnit.DAYS);
        }

        return result;
    }

    public void setPostViewCount(Long postId, int count) {
        String key = getPostViewKey(postId);
        redisTemplate.opsForValue().set(key, count);
        // TTL 7일 설정
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }

    public Integer getPostViewCount(Long postId) {
        String key = getPostViewKey(postId);
        Integer count = (Integer) redisTemplate.opsForValue().get(key);

        // 조회 시 TTL 연장 (7일)
        if (count != null) {
            redisTemplate.expire(key, 7, TimeUnit.DAYS);
        }

        return count;
    }

    // --- Comment Count Logic ---
    public Long incrementPostCommentCount(Long postId) {
        String key = getPostCommentCountKey(postId);
        Long result = executeScript(INCREMENT_COUNTER_SCRIPT, Long.class, List.of(key));

        // 접근 시 TTL 연장 (7일)
        if (result != null && result != -1) {
            redisTemplate.expire(key, 7, TimeUnit.DAYS);
        }

        return result;
    }

    public Long decreasePostCommentCount(Long postId) {
        String key = getPostCommentCountKey(postId);
        Long result = executeScript(UPDATE_COUNTER_SCRIPT, Long.class, List.of(key), "-1");

        // 접근 시 TTL 연장 (7일)
        if (result != null && result != -1) {
            redisTemplate.expire(key, 7, TimeUnit.DAYS);
        }

        return result;
    }

    public void setPostCommentCount(Long postId, int count) {
        String key = getPostCommentCountKey(postId);
        redisTemplate.opsForValue().set(key, count);
        // TTL 7일 설정
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }

    public Integer getPostCommentCount(Long postId) {
        String key = getPostCommentCountKey(postId);
        Integer count = (Integer) redisTemplate.opsForValue().get(key);

        // 조회 시 TTL 연장 (7일)
        if (count != null) {
            redisTemplate.expire(key, 7, TimeUnit.DAYS);
        }

        return count;
    }

    // --- Post Like Logic ---
    public Long togglePostLike(Long postId, Long userId) {
        String likeSetKey = getPostLikeSetKey(postId);
        String countKey = getPostLikeCountKey(postId);

        Long result = executeScript(TOGGLE_LIKE_SCRIPT, Long.class,
                Arrays.asList(likeSetKey, countKey),
                userId.toString());

        // 접근 시 TTL 연장 (7일)
        if (result != null && result != -1) {
            redisTemplate.expire(likeSetKey, 7, TimeUnit.DAYS);
            redisTemplate.expire(countKey, 7, TimeUnit.DAYS);
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

        // TTL 7일
        redisTemplate.expire(likeSetKey, 7, TimeUnit.DAYS);
        redisTemplate.expire(countKey, 7, TimeUnit.DAYS);
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

        // 조회 시 TTL 연장 (7일)
        if (count != null) {
            redisTemplate.expire(key, 7, TimeUnit.DAYS);
            redisTemplate.expire(getPostLikeSetKey(postId), 7, TimeUnit.DAYS);
        }

        return count;
    }

    public void addPostLikeEvent(Long postId, Long userId, boolean isAdded) {
        String event = postId + ":" + userId + ":" + (isAdded ? "ADD" : "REMOVE");
        stringRedisTemplate.opsForList().rightPush(POST_LIKE_EVENT_QUEUE, event);
    }

    // --- Comment Like Logic ---
    public Long toggleCommentLike(Long commentId, Long userId) {
        String likeSetKey = getCommentLikeSetKey(commentId);
        String countKey = getCommentLikeCountKey(commentId);

        Long result = executeScript(TOGGLE_LIKE_SCRIPT, Long.class,
                Arrays.asList(likeSetKey, countKey),
                userId.toString());

        // 접근 시 TTL 연장 (7일)
        if (result != null && result != -1) {
            redisTemplate.expire(likeSetKey, 7, TimeUnit.DAYS);
            redisTemplate.expire(countKey, 7, TimeUnit.DAYS);
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

        redisTemplate.expire(likeSetKey, 7, TimeUnit.DAYS);
        redisTemplate.expire(countKey, 7, TimeUnit.DAYS);
    }

    public boolean isCommentLiked(Long commentId, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(getCommentLikeSetKey(commentId), userId.toString()));
    }

    public boolean hasCommentLikeKey(Long commentId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(getCommentLikeSetKey(commentId)));
    }

    public Integer getCommentLikeCount(Long commentId) {
        String key = getCommentLikeCountKey(commentId);
        Integer count = (Integer) redisTemplate.opsForValue().get(key);

        // 조회 시 TTL 연장 (7일)
        if (count != null) {
            redisTemplate.expire(key, 7, TimeUnit.DAYS);
            redisTemplate.expire(getCommentLikeSetKey(commentId), 7, TimeUnit.DAYS);
        }

        return count;
    }

    public void addCommentLikeEvent(Long commentId, Long userId, boolean isAdded) {
        String event = commentId + ":" + userId + ":" + (isAdded ? "ADD" : "REMOVE");
        stringRedisTemplate.opsForList().rightPush(COMMENT_LIKE_EVENT_QUEUE, event);
    }

    // --- Helper Method ---
    private <T> T executeScript(String scriptText, Class<T> returnType, List<String> keys, Object... args) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>(scriptText, returnType);
        return redisTemplate.execute(script, keys, args);
    }

    // ============================================================
    // Bulk Operations (for Post List - MGET)
    // ============================================================

    /**
     * 여러 게시글의 조회수를 한 번에 조회 (MGET)
     * @param postIds 게시글 ID 목록
     * @return postId -> viewCount 맵
     */
    public Map<Long, Integer> getBulkViewCounts(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<String> keys = postIds.stream().map(this::getPostViewKey).toList();
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        
        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < postIds.size(); i++) {
            Object value = values != null ? values.get(i) : null;
            result.put(postIds.get(i), value != null ? (Integer) value : null);
        }
        return result;
    }

    /**
     * 여러 게시글의 좋아요수를 한 번에 조회 (MGET)
     */
    public Map<Long, Integer> getBulkLikeCounts(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<String> keys = postIds.stream().map(this::getPostLikeCountKey).toList();
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        
        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < postIds.size(); i++) {
            Object value = values != null ? values.get(i) : null;
            result.put(postIds.get(i), value != null ? (Integer) value : null);
        }
        return result;
    }

    /**
     * 여러 게시글의 댓글수를 한 번에 조회 (MGET)
     */
    public Map<Long, Integer> getBulkCommentCounts(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<String> keys = postIds.stream().map(this::getPostCommentCountKey).toList();
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        
        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < postIds.size(); i++) {
            Object value = values != null ? values.get(i) : null;
            result.put(postIds.get(i), value != null ? (Integer) value : null);
        }
        return result;
    }

    // ============================================================
    // Batch Operations (for Scheduler)
    // ============================================================

    /**
     * Redis에서 조회수 데이터 수집 (Scan)
     * @return postId -> viewCount 맵
     */
    public Map<Long, Integer> scanAndCollectViewCounts() {
        ScanOptions options = ScanOptions.scanOptions()
                .match("post:*:viewCount")
                .count(100)
                .build();

        Map<Long, Integer> result = new HashMap<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    Long postId = extractPostIdFromKey(key);
                    Integer count = getPostViewCount(postId);
                    if (count != null) {
                        result.put(postId, count);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse Redis key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan Redis for view counts", e);
        }

        return result;
    }

    /**
     * Redis에서 댓글수 데이터 수집 (Scan)
     * @return postId -> commentCount 맵
     */
    public Map<Long, Integer> scanAndCollectCommentCounts() {
        ScanOptions options = ScanOptions.scanOptions()
                .match("post:*:commentCount")
                .count(100)
                .build();

        Map<Long, Integer> result = new HashMap<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    Long postId = extractPostIdFromKey(key);
                    Integer count = getPostCommentCount(postId);
                    if (count != null) {
                        result.put(postId, count);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse Redis key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan Redis for comment counts", e);
        }

        return result;
    }

    /**
     * Redis에서 게시글 좋아요수 데이터 수집 (Scan)
     * @return postId -> likeCount 맵
     */
    public Map<Long, Integer> scanAndCollectPostLikeCounts() {
        ScanOptions options = ScanOptions.scanOptions()
                .match("post:*:likeCount")
                .count(100)
                .build();

        Map<Long, Integer> result = new HashMap<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    Long postId = extractPostIdFromKey(key);
                    Integer count = getPostLikeCount(postId);
                    if (count != null) {
                        result.put(postId, count);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse Redis key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan Redis for post like counts", e);
        }

        return result;
    }

    /**
     * Redis에서 댓글 좋아요수 데이터 수집 (Scan)
     * @return commentId -> likeCount 맵
     */
    public Map<Long, Integer> scanAndCollectCommentLikeCounts() {
        ScanOptions options = ScanOptions.scanOptions()
                .match("comment:*:likeCount")
                .count(100)
                .build();

        Map<Long, Integer> result = new HashMap<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    Long commentId = extractCommentIdFromKey(key);
                    Integer count = getCommentLikeCount(commentId);
                    if (count != null) {
                        result.put(commentId, count);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse Redis key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan Redis for comment like counts", e);
        }

        return result;
    }

    /**
     * 게시글 좋아요 이벤트 큐에서 데이터 가져오기
     * @param maxSize 최대 가져올 이벤트 수
     * @return LikeEvent 리스트
     */
    public List<LikeEvent> pollPostLikeEvents(int maxSize) {
        List<String> events = stringRedisTemplate.opsForList()
                .range(POST_LIKE_EVENT_QUEUE, 0, maxSize - 1);

        if (events == null || events.isEmpty()) {
            return List.of();
        }

        return events.stream()
                .map(this::parseLikeEvent)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 댓글 좋아요 이벤트 큐에서 데이터 가져오기
     * @param maxSize 최대 가져올 이벤트 수
     * @return LikeEvent 리스트
     */
    public List<LikeEvent> pollCommentLikeEvents(int maxSize) {
        List<String> events = stringRedisTemplate.opsForList()
                .range(COMMENT_LIKE_EVENT_QUEUE, 0, maxSize - 1);

        if (events == null || events.isEmpty()) {
            return List.of();
        }

        return events.stream()
                .map(this::parseLikeEvent)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 처리된 게시글 좋아요 이벤트 제거
     * @param processedCount 처리된 이벤트 수
     */
    public void trimPostLikeEvents(int processedCount) {
        stringRedisTemplate.opsForList().trim(POST_LIKE_EVENT_QUEUE, processedCount, -1);
    }

    /**
     * 처리된 댓글 좋아요 이벤트 제거
     * @param processedCount 처리된 이벤트 수
     */
    public void trimCommentLikeEvents(int processedCount) {
        stringRedisTemplate.opsForList().trim(COMMENT_LIKE_EVENT_QUEUE, processedCount, -1);
    }

    /**
     * Redis 키 삭제 메서드들
     */
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

    // --- Private Helper Methods ---

    /**
     * Redis 키에서 postId 추출
     * 예: "post:123:viewCount" -> 123
     */
    private Long extractPostIdFromKey(String key) {
        String[] parts = key.split(":");
        return Long.parseLong(parts[1]);
    }

    /**
     * Redis 키에서 commentId 추출
     * 예: "comment:456:likeCount" -> 456
     */
    private Long extractCommentIdFromKey(String key) {
        String[] parts = key.split(":");
        return Long.parseLong(parts[1]);
    }

    /**
     * 이벤트 문자열을 LikeEvent 객체로 파싱
     * 예: "123:456:ADD" -> LikeEvent(123, 456, true)
     */
    private LikeEvent parseLikeEvent(String event) {
        try {
            String[] parts = event.split(":");
            if (parts.length == 3) {
                return new LikeEvent(
                        Long.parseLong(parts[0]),
                        Long.parseLong(parts[1]),
                        "ADD".equals(parts[2])
                );
            }
        } catch (Exception e) {
            log.error("Failed to parse like event: {}", event, e);
        }
        return null;
    }

    /**
     * 좋아요 이벤트 DTO
     * @param targetId postId 또는 commentId
     * @param userId 유저 ID
     * @param isAdd true면 추가, false면 제거
     */
    public record LikeEvent(Long targetId, Long userId, boolean isAdd) {}
}