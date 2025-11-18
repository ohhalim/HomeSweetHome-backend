package com.homesweet.homesweetback.domain.community.controller;

import com.homesweet.homesweetback.domain.community.dto.CommunityPostResponse;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.service.PostSortingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Post Sorting Controller
 *
 * Reddit-style 게시글 정렬 API (Hot, Top, Controversial, Rising)
 *
 * @author ohhalim777@gmail.com
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Post Sorting", description = "게시글 정렬 API (Reddit-style)")
public class PostSortingController {

    private final PostSortingService sortingService;

    /**
     * HOT 게시글 조회
     */
    @GetMapping("/posts/hot")
    @Operation(
        summary = "HOT 게시글",
        description = "최근 + 인기 있는 게시글을 조회합니다 (Reddit HOT 알고리즘)"
    )
    public ResponseEntity<Page<CommunityPostResponse>> getHotPosts(
            @RequestParam(required = false) @Parameter(description = "서브레딧 ID") Long subredditId,
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<CommunityPostEntity> posts = sortingService.getHotPosts(pageable, subredditId);

        return ResponseEntity.ok(posts.map(CommunityPostResponse::from));
    }

    /**
     * TOP 게시글 조회
     */
    @GetMapping("/posts/top")
    @Operation(
        summary = "TOP 게시글",
        description = "기간별 최고 점수 게시글을 조회합니다"
    )
    public ResponseEntity<Page<CommunityPostResponse>> getTopPosts(
            @RequestParam(required = false) @Parameter(description = "서브레딧 ID") Long subredditId,
            @RequestParam(defaultValue = "DAY") @Parameter(
                description = "조회 기간",
                example = "DAY"
            ) PostSortingService.TopPeriod period,
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<CommunityPostEntity> posts = sortingService.getTopPosts(pageable, subredditId, period);

        return ResponseEntity.ok(posts.map(CommunityPostResponse::from));
    }

    /**
     * NEW 게시글 조회
     */
    @GetMapping("/posts/new")
    @Operation(summary = "NEW 게시글", description = "최신순 게시글을 조회합니다")
    public ResponseEntity<Page<CommunityPostResponse>> getNewPosts(
            @RequestParam(required = false) @Parameter(description = "서브레딧 ID") Long subredditId,
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<CommunityPostEntity> posts = sortingService.getNewPosts(pageable, subredditId);

        return ResponseEntity.ok(posts.map(CommunityPostResponse::from));
    }

    /**
     * CONTROVERSIAL 게시글 조회
     */
    @GetMapping("/posts/controversial")
    @Operation(
        summary = "CONTROVERSIAL 게시글",
        description = "논쟁적인 게시글을 조회합니다 (Upvote와 Downvote 비율이 비슷한 글)"
    )
    public ResponseEntity<Page<CommunityPostResponse>> getControversialPosts(
            @RequestParam(required = false) @Parameter(description = "서브레딧 ID") Long subredditId,
            @RequestParam(defaultValue = "DAY") @Parameter(
                description = "조회 기간",
                example = "DAY"
            ) PostSortingService.TopPeriod period,
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<CommunityPostEntity> posts = sortingService.getControversialPosts(pageable, subredditId, period);

        return ResponseEntity.ok(posts.map(CommunityPostResponse::from));
    }

    /**
     * RISING 게시글 조회
     */
    @GetMapping("/posts/rising")
    @Operation(
        summary = "RISING 게시글",
        description = "급상승 게시글을 조회합니다 (최근 빠르게 인기를 얻는 글)"
    )
    public ResponseEntity<Page<CommunityPostResponse>> getRisingPosts(
            @RequestParam(required = false) @Parameter(description = "서브레딧 ID") Long subredditId,
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<CommunityPostEntity> posts = sortingService.getRisingPosts(pageable, subredditId);

        return ResponseEntity.ok(posts.map(CommunityPostResponse::from));
    }

    /**
     * 서브레딧별 HOT 게시글
     */
    @GetMapping("/subreddits/r/{name}/posts/hot")
    @Operation(summary = "서브레딧 HOT 게시글", description = "특정 서브레딧의 HOT 게시글을 조회합니다")
    public ResponseEntity<Page<CommunityPostResponse>> getSubredditHotPosts(
            @PathVariable @Parameter(description = "서브레딧 이름", example = "programming") String name,
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size) {

        // TODO: 서브레딧 이름으로 ID 조회 후 사용
        Pageable pageable = PageRequest.of(page, size);
        Page<CommunityPostEntity> posts = sortingService.getHotPosts(pageable, null);

        return ResponseEntity.ok(posts.map(CommunityPostResponse::from));
    }
}
