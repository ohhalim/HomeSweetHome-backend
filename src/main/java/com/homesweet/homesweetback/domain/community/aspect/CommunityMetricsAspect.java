package com.homesweet.homesweetback.domain.community.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 커뮤니티 도메인 전용 성능 메트릭 수집 Aspect
 *
 * 수집되는 메트릭:
 * - 게시글 생성/조회/수정/삭제 시간 및 횟수
 * - 댓글 생성/조회/수정/삭제 시간 및 횟수
 * - 좋아요 토글 시간 및 횟수
 * - 조회수 증가 시간 및 횟수
 * - 동시성 제어 실패 횟수
 * - 비즈니스 로직 실행 시간 분포
 *
 * @author HomeSweetHome Team
 * @date 2025-01-17
 */
@Slf4j
@Aspect
@Component
@Profile("!test")  // 테스트 환경에서는 비활성화
@RequiredArgsConstructor
public class CommunityMetricsAspect {

    private final MeterRegistry meterRegistry;

    // 메트릭 캐시 (성능 최적화)
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    /**
     * 커뮤니티 서비스 메서드 실행 시간 측정
     */
    @Around("execution(* com.homesweet.homesweetback.domain.community.service.CommunityPostService.*(..))")
    public Object measurePostServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureAndRecord(joinPoint, "community.post.service");
    }

    /**
     * 댓글 서비스 메서드 실행 시간 측정
     */
    @Around("execution(* com.homesweet.homesweetback.domain.community.service.CommunityCommentService.*(..))")
    public Object measureCommentServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureAndRecord(joinPoint, "community.comment.service");
    }

    /**
     * 조회수/좋아요 서비스 메서드 실행 시간 측정
     */
    @Around("execution(* com.homesweet.homesweetback.domain.community.service.CommunityCountService.*(..))")
    public Object measureCountServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureAndRecord(joinPoint, "community.count.service");
    }

    /**
     * 커뮤니티 리포지토리 쿼리 실행 시간 측정
     */
    @Around("execution(* com.homesweet.homesweetback.domain.community.repository.*Repository.*(..))")
    public Object measureRepositoryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureAndRecord(joinPoint, "community.repository");
    }

    /**
     * 메서드 실행 시간 측정 및 기록
     */
    private Object measureAndRecord(ProceedingJoinPoint joinPoint, String metricPrefix) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        String className = signature.getDeclaringType().getSimpleName();

        // Timer 메트릭 생성 또는 캐시에서 가져오기
        String timerKey = metricPrefix + "." + methodName;
        Timer timer = timerCache.computeIfAbsent(timerKey, key ->
                Timer.builder(key)
                        .description("Execution time for " + className + "." + methodName)
                        .tag("class", className)
                        .tag("method", methodName)
                        .tag("domain", "community")
                        .publishPercentiles(0.5, 0.95, 0.99) // p50, p95, p99
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );

        // 실행 횟수 카운터
        String counterKey = metricPrefix + "." + methodName + ".count";
        Counter counter = counterCache.computeIfAbsent(counterKey, key ->
                Counter.builder(key)
                        .description("Invocation count for " + className + "." + methodName)
                        .tag("class", className)
                        .tag("method", methodName)
                        .tag("domain", "community")
                        .register(meterRegistry)
        );

        // 타이머 시작
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Object result = joinPoint.proceed();

            // 성공 카운터 증가
            counter.increment();

            // 메서드별 특화 메트릭 기록
            recordSpecificMetrics(methodName, className, result);

            return result;

        } catch (Exception e) {
            // 실패 카운터
            String errorCounterKey = metricPrefix + "." + methodName + ".error.count";
            Counter errorCounter = counterCache.computeIfAbsent(errorCounterKey, key ->
                    Counter.builder(key)
                            .description("Error count for " + className + "." + methodName)
                            .tag("class", className)
                            .tag("method", methodName)
                            .tag("domain", "community")
                            .tag("error_type", e.getClass().getSimpleName())
                            .register(meterRegistry)
            );
            errorCounter.increment();

            // 예외별 메트릭
            recordExceptionMetrics(methodName, e);

            throw e;

        } finally {
            // 타이머 종료 및 기록
            sample.stop(timer);
        }
    }

    /**
     * 메서드별 특화 메트릭 기록
     */
    private void recordSpecificMetrics(String methodName, String className, Object result) {
        // 게시글 생성
        if (methodName.equals("createPost")) {
            incrementCounter("community.post.created.total", "operation", "create");
        }
        // 게시글 조회
        else if (methodName.equals("getPost") || methodName.equals("getPostList")) {
            incrementCounter("community.post.viewed.total", "operation", "read");
        }
        // 게시글 수정
        else if (methodName.equals("updatePost")) {
            incrementCounter("community.post.updated.total", "operation", "update");
        }
        // 게시글 삭제
        else if (methodName.equals("deletePost")) {
            incrementCounter("community.post.deleted.total", "operation", "delete");
        }
        // 댓글 생성
        else if (methodName.equals("createComment")) {
            incrementCounter("community.comment.created.total", "operation", "create");
        }
        // 댓글 조회
        else if (methodName.equals("getCommentList")) {
            incrementCounter("community.comment.viewed.total", "operation", "read");
        }
        // 댓글 수정
        else if (methodName.equals("updateComment")) {
            incrementCounter("community.comment.updated.total", "operation", "update");
        }
        // 댓글 삭제
        else if (methodName.equals("deleteComment")) {
            incrementCounter("community.comment.deleted.total", "operation", "delete");
        }
        // 조회수 증가
        else if (methodName.equals("increaseViews")) {
            incrementCounter("community.views.increased.total", "operation", "increment");
        }
        // 게시글 좋아요
        else if (methodName.equals("togglePostLike")) {
            incrementCounter("community.post.like.toggled.total", "operation", "toggle");
        }
        // 댓글 좋아요
        else if (methodName.equals("toggleCommentLike")) {
            incrementCounter("community.comment.like.toggled.total", "operation", "toggle");
        }
    }

    /**
     * 예외별 메트릭 기록
     */
    private void recordExceptionMetrics(String methodName, Exception e) {
        String exceptionType = e.getClass().getSimpleName();

        // 동시성 제어 실패 (OptimisticLockException, PessimisticLockException 등)
        if (exceptionType.contains("Lock")) {
            incrementCounter("community.concurrency.failure.total",
                    "method", methodName,
                    "exception", exceptionType);
        }
        // 엔티티를 찾지 못한 경우
        else if (exceptionType.contains("NotFound") || exceptionType.contains("EntityNotFound")) {
            incrementCounter("community.entity.not_found.total",
                    "method", methodName,
                    "exception", exceptionType);
        }
        // 권한 없음
        else if (exceptionType.contains("Forbidden") || exceptionType.contains("Unauthorized")) {
            incrementCounter("community.authorization.failure.total",
                    "method", methodName,
                    "exception", exceptionType);
        }
        // 기타 에러
        else {
            incrementCounter("community.error.total",
                    "method", methodName,
                    "exception", exceptionType);
        }
    }

    /**
     * 카운터 증가 헬퍼 메서드
     */
    private void incrementCounter(String name, String... tags) {
        Counter counter = counterCache.computeIfAbsent(name + String.join("_", tags), key -> {
            Counter.Builder builder = Counter.builder(name)
                    .description("Community domain metric: " + name)
                    .tag("domain", "community");

            // 태그 추가
            for (int i = 0; i < tags.length; i += 2) {
                if (i + 1 < tags.length) {
                    builder.tag(tags[i], tags[i + 1]);
                }
            }

            return builder.register(meterRegistry);
        });

        counter.increment();
    }
}
