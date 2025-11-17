package com.homesweet.homesweetback.domain.community.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * ML 서비스 클라이언트
 *
 * FastAPI ML 마이크로서비스와 통신하는 클라이언트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MLServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${ml-service.base-url:http://localhost:8000}")
    private String mlServiceBaseUrl;

    @Value("${ml-service.timeout:10}")
    private int timeout;

    /**
     * 게시글 추천 요청
     *
     * @param userId 사용자 ID
     * @param likedPosts 좋아한 게시글 ID 리스트
     * @param k 추천할 게시글 수
     * @return 추천 결과
     */
    public Mono<MLRecommendationResponse> getRecommendations(
            Long userId,
            List<Long> likedPosts,
            int k
    ) {
        var request = Map.of(
                "user_id", userId,
                "user_liked_posts", likedPosts != null ? likedPosts : List.of(),
                "k", k,
                "use_hybrid", true
        );

        return buildWebClient()
                .post()
                .uri("/api/v1/ml/community/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MLRecommendationResponse.class)
                .timeout(Duration.ofSeconds(timeout))
                .doOnError(error -> log.error("ML recommendation request failed", error))
                .onErrorResume(this::handleError);
    }

    /**
     * 스팸 탐지 요청
     *
     * @param text 검사할 텍스트
     * @return 스팸 탐지 결과
     */
    public Mono<MLSpamDetectionResponse> detectSpam(String text) {
        return detectSpam(text, 0.5);
    }

    /**
     * 스팸 탐지 요청 (임계값 지정)
     *
     * @param text 검사할 텍스트
     * @param threshold 스팸 판정 임계값
     * @return 스팸 탐지 결과
     */
    public Mono<MLSpamDetectionResponse> detectSpam(String text, double threshold) {
        var request = Map.of(
                "text", text,
                "threshold", threshold
        );

        return buildWebClient()
                .post()
                .uri("/api/v1/ml/community/spam/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MLSpamDetectionResponse.class)
                .timeout(Duration.ofSeconds(timeout))
                .doOnError(error -> log.error("ML spam detection request failed", error))
                .onErrorResume(this::handleError);
    }

    /**
     * 태그 추출 요청
     *
     * @param title 게시글 제목
     * @param content 게시글 내용
     * @param maxTags 최대 태그 수
     * @return 태그 추출 결과
     */
    public Mono<MLTagExtractionResponse> extractTags(
            String title,
            String content,
            int maxTags
    ) {
        var request = Map.of(
                "title", title,
                "content", content,
                "max_tags", maxTags,
                "min_score", 0.1
        );

        return buildWebClient()
                .post()
                .uri("/api/v1/ml/community/tags/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MLTagExtractionResponse.class)
                .timeout(Duration.ofSeconds(timeout))
                .doOnError(error -> log.error("ML tag extraction request failed", error))
                .onErrorResume(this::handleError);
    }

    /**
     * 트렌딩 게시글 예측 요청
     *
     * @param posts 게시글 메트릭 리스트
     * @param topK 상위 K개
     * @return 트렌딩 예측 결과
     */
    public Mono<MLTrendingPredictionResponse> predictTrending(
            List<Map<String, Object>> posts,
            int topK
    ) {
        var request = Map.of(
                "posts", posts,
                "top_k", topK,
                "time_window_hours", 24
        );

        return buildWebClient()
                .post()
                .uri("/api/v1/ml/community/trending/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MLTrendingPredictionResponse.class)
                .timeout(Duration.ofSeconds(timeout))
                .doOnError(error -> log.error("ML trending prediction request failed", error))
                .onErrorResume(this::handleError);
    }

    /**
     * ML 서비스 헬스체크
     *
     * @return 헬스체크 결과
     */
    public Mono<Map<String, Object>> healthCheck() {
        return buildWebClient()
                .get()
                .uri("/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .doOnError(error -> log.warn("ML service health check failed", error))
                .onErrorReturn(Map.of("status", "unavailable"));
    }

    // ========== Private Methods ==========

    private WebClient buildWebClient() {
        return webClientBuilder
                .baseUrl(mlServiceBaseUrl)
                .build();
    }

    private <T> Mono<T> handleError(Throwable error) {
        if (error instanceof WebClientResponseException) {
            WebClientResponseException webClientException = (WebClientResponseException) error;
            HttpStatus statusCode = (HttpStatus) webClientException.getStatusCode();

            log.error("ML Service error - Status: {}, Response: {}",
                    statusCode,
                    webClientException.getResponseBodyAsString());
        }

        return Mono.empty();
    }

    // ========== Response DTOs ==========

    public record MLRecommendationResponse(
            Long userId,
            List<RecommendationItem> recommendations,
            int totalCount
    ) {
        public record RecommendationItem(
                Long postId,
                double score,
                int rank,
                String reason
        ) {}
    }

    public record MLSpamDetectionResponse(
            boolean isSpam,
            double confidence,
            double spamScore,
            String category,
            List<String> reasons
    ) {}

    public record MLTagExtractionResponse(
            List<TagItem> tags,
            int totalCount
    ) {
        public record TagItem(
                String tag,
                double score,
                String category
        ) {}
    }

    public record MLTrendingPredictionResponse(
            List<Map<String, Object>> trendingPosts,
            int totalCount,
            int timeWindowHours
    ) {}
}
