package com.homesweet.homesweetback.domain.community.controller;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2UserPrincipal;
import com.homesweet.homesweetback.domain.community.dto.SubredditCreateRequest;
import com.homesweet.homesweetback.domain.community.dto.SubredditResponse;
import com.homesweet.homesweetback.domain.community.entity.SubredditEntity;
import com.homesweet.homesweetback.domain.community.service.SubredditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Subreddit Controller
 *
 * Reddit-style 서브레딧 관리 API
 *
 * @author ohhalim777@gmail.com
 */
@RestController
@RequestMapping("/api/v1/subreddits")
@RequiredArgsConstructor
@Tag(name = "Subreddit", description = "서브레딧 관리 API")
public class SubredditController {

    private final SubredditService subredditService;

    /**
     * 서브레딧 생성
     */
    @PostMapping
    @Operation(summary = "서브레딧 생성", description = "새로운 서브레딧을 생성합니다. 생성자는 자동으로 모더레이터가 됩니다.")
    public ResponseEntity<SubredditResponse> createSubreddit(
            @RequestBody @Valid SubredditCreateRequest request,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        SubredditEntity subreddit = subredditService.createSubreddit(
                request.getName(),
                request.getTitle(),
                request.getDescription(),
                request.getIsPrivate(),
                request.getIsNsfw(),
                userId
        );

        boolean isSubscribed = subredditService.isSubscribed(subreddit.getSubredditId(), userId);
        boolean isModerator = subredditService.hasModeratorPermission(
                subreddit.getSubredditId(), userId, null);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SubredditResponse.from(subreddit, isSubscribed, isModerator));
    }

    /**
     * 서브레딧 조회 (이름으로)
     */
    @GetMapping("/r/{name}")
    @Operation(summary = "서브레딧 조회", description = "서브레딧 이름으로 조회합니다 (예: /r/programming)")
    public ResponseEntity<SubredditResponse> getSubredditByName(
            @PathVariable @Parameter(description = "서브레딧 이름", example = "programming") String name,
            Authentication authentication) {

        SubredditEntity subreddit = subredditService.getSubredditByName(name);

        Long userId = null;
        boolean isSubscribed = false;
        boolean isModerator = false;

        if (authentication != null && authentication.isAuthenticated()) {
            OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
            userId = principal.getUserId();
            isSubscribed = subredditService.isSubscribed(subreddit.getSubredditId(), userId);
            isModerator = subredditService.hasModeratorPermission(
                    subreddit.getSubredditId(), userId, null);
        }

        return ResponseEntity.ok(SubredditResponse.from(subreddit, isSubscribed, isModerator));
    }

    /**
     * 인기 서브레딧 목록
     */
    @GetMapping("/popular")
    @Operation(summary = "인기 서브레딧 목록", description = "구독자 수 기준 인기 서브레딧 목록을 조회합니다")
    public ResponseEntity<Page<SubredditResponse>> getPopularSubreddits(
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SubredditEntity> subreddits = subredditService.getPopularSubreddits(pageable);

        return ResponseEntity.ok(subreddits.map(SubredditResponse::from));
    }

    /**
     * 서브레딧 검색
     */
    @GetMapping("/search")
    @Operation(summary = "서브레딧 검색", description = "키워드로 서브레딧을 검색합니다")
    public ResponseEntity<Page<SubredditResponse>> searchSubreddits(
            @RequestParam @Parameter(description = "검색 키워드", example = "programming") String keyword,
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SubredditEntity> subreddits = subredditService.searchSubreddits(keyword, pageable);

        return ResponseEntity.ok(subreddits.map(SubredditResponse::from));
    }

    /**
     * 서브레딧 구독
     */
    @PostMapping("/{id}/subscribe")
    @Operation(summary = "서브레딧 구독", description = "서브레딧을 구독합니다")
    public ResponseEntity<Void> subscribeToSubreddit(
            @PathVariable @Parameter(description = "서브레딧 ID") Long id,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        subredditService.subscribeToSubreddit(id, userId);

        return ResponseEntity.ok().build();
    }

    /**
     * 서브레딧 구독 취소
     */
    @DeleteMapping("/{id}/subscribe")
    @Operation(summary = "서브레딧 구독 취소", description = "서브레딧 구독을 취소합니다")
    public ResponseEntity<Void> unsubscribeFromSubreddit(
            @PathVariable @Parameter(description = "서브레딧 ID") Long id,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        subredditService.unsubscribeFromSubreddit(id, userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * 내가 구독한 서브레딧 목록
     */
    @GetMapping("/my/subscriptions")
    @Operation(summary = "내 구독 목록", description = "내가 구독한 서브레딧 목록을 조회합니다")
    public ResponseEntity<Page<SubredditResponse>> getMySubscriptions(
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        Pageable pageable = PageRequest.of(page, size);
        Page<SubredditEntity> subreddits = subredditService.getUserSubscriptions(userId, pageable);

        return ResponseEntity.ok(subreddits.map(SubredditResponse::from));
    }
}
