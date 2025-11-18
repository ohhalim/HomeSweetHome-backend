package com.homesweet.homesweetback.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
    private static final long COMMUNITY_CAPACITY = 50;
    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(1);
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = getClientIP(request) + ":" + request.getRequestURI();
        Bucket bucket = cache.computeIfAbsent(key, k -> createBucket());

        if (bucket.tryConsume(1)) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.getWriter().write("{\"error\":\"Too many requests\"}");
        log.warn("Rate limit exceeded for: {}", key);
        return false;
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(COMMUNITY_CAPACITY, Refill.intervally(COMMUNITY_CAPACITY, DEFAULT_DURATION));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader == null ? request.getRemoteAddr() : xfHeader.split(",")[0];
    }
}
