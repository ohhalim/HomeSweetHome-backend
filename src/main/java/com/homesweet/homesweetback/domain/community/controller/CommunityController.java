package com.homesweet.homesweetback.domain.community.controller;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2UserPrincipal;
import com.homesweet.homesweetback.domain.community.dto.CommunityCommentRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityCommentResponse;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostResponse;
import com.homesweet.homesweetback.domain.community.service.CommunityCommentService;
import com.homesweet.homesweetback.domain.community.service.CommunityCountService;
import com.homesweet.homesweetback.domain.community.service.CommunityPostService;
import com.homesweet.homesweetback.domain.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 로그 확인용
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j // 로그 추가
@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityPostService communityPostService;
    private final CommunityCommentService communityCommentService;
    private final CommunityCountService communityCountService;
    private final SubscriptionService subscriptionService;

    // ==========================================
    // 💡 핵심: 안전하게 User ID를 가져오는 헬퍼 메서드
    // ==========================================
    private Long getUserId(Authentication authentication) {
        // 1. 인증 정보가 없거나, 익명 사용자("anonymousUser")인 경우 -> 테스트용 ID 반환
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getPrincipal() instanceof String) {
            log.warn("⚠ 인증되지 않은 요청입니다. 테스트용 ID(1L)를 사용합니다.");
            return 1L; // DB에 존재하는 테스트용 유저 ID (k6 테스트용)
        }

        // 2. 정상 로그인 유저인 경우 -> 토큰에서 ID 추출
        try {
            OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
            return principal.getUserId();
        } catch (ClassCastException e) {
            log.error("Authentication Casting Error: {}", authentication.getPrincipal());
            return 1L; // 캐스팅 실패 시에도 테스트 ID 반환 (방어 코드)
        }
    }

    /**
     * 게시글 작성 API
     */
    @PostMapping("/posts")
    public ResponseEntity<CommunityPostResponse> createPost(
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart("request") @Valid CommunityPostRequest request,
            @RequestParam(required = false) Long testUserId,
            Authentication authentication) {
        Long userId = testUserId != null ? testUserId : getUserId(authentication);
        CommunityPostResponse response = communityPostService.createPost(images, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 게시글 단건 조회 API (프리미엄 구독자 전용)
     */
    @GetMapping("/posts/{postId}")
    public ResponseEntity<CommunityPostResponse> getPost(
            @PathVariable Long postId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        subscriptionService.validateSubscription(userId);
        CommunityPostResponse response = communityPostService.getPost(postId);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시글 수정 API
     */
    @PutMapping("/posts/{postId}")
    public ResponseEntity<CommunityPostResponse> updatePost(
            @PathVariable Long postId,
            @RequestBody @Valid CommunityPostRequest request,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        CommunityPostResponse response = communityPostService.updatePost(postId, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시글 삭제 API
     */
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long postId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        communityPostService.deletePost(postId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 댓글 작성 API
     */
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommunityCommentResponse> createComment(
            @PathVariable Long postId,
            @RequestBody @Valid CommunityCommentRequest request,
            @RequestParam(required = false) Long testUserId,
            Authentication authentication) {
        Long userId = testUserId != null ? testUserId : getUserId(authentication);
        CommunityCommentResponse response = communityCommentService.createComment(postId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 해당 게시글 모든 댓글 조회 (프리미엄 구독자 전용)
     */
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommunityCommentResponse>> getComments(
            @PathVariable Long postId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        subscriptionService.validateSubscription(userId);
        List<CommunityCommentResponse> responses = communityCommentService.getCommentsByPostId(postId);
        return ResponseEntity.ok(responses);
    }

    /**
     * 댓글 수정 API
     */
    @PutMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<CommunityCommentResponse> updateComment(
            @PathVariable Long commentId,
            @RequestBody @Valid CommunityCommentRequest request,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        CommunityCommentResponse response = communityCommentService.updateComment(commentId, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 댓글 삭제 API
     */
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        communityCommentService.deleteComment(commentId, postId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 게시글 조회수 초기화 (Redis)
     */
    @PostMapping("/posts/{postId}/views/init")
    public ResponseEntity<Void> initViewCountFromDB(@PathVariable Long postId) {
        communityCountService.initViewCountFromDB(postId);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시글 조회수 증가 (로그인 여부 상관없음)
     */
    @PostMapping("/posts/{postId}/views")
    public ResponseEntity<Void> increaseViewCount(@PathVariable Long postId) {
        communityCountService.increaseViewCount(postId);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시글 좋아요 토글
     */
    @PostMapping("/posts/{postId}/likes")
    public ResponseEntity<Void> togglePostLike(
            @PathVariable Long postId,
            @RequestParam(required = false) Long testUserId,
            Authentication authentication) {
        Long userId = testUserId != null ? testUserId : getUserId(authentication);
        communityCountService.togglePostLike(postId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시글 좋아요 상태 확인
     */
    @GetMapping("/posts/{postId}/likes/status")
    public ResponseEntity<Boolean> getPostLikeStatus(
            @PathVariable Long postId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        boolean isLiked = communityCountService.isPostLiked(postId, userId);
        return ResponseEntity.ok(isLiked);
    }

    /**
     * 댓글 좋아요 토글
     */
    @PostMapping("/posts/{postId}/comments/{commentId}/likes")
    public ResponseEntity<Void> toggleCommentLike(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        communityCountService.toggleCommentLike(commentId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 댓글 좋아요 상태 확인
     */
    @GetMapping("/posts/{postId}/comments/{commentId}/likes/status")
    public ResponseEntity<Boolean> getCommentLikeStatus(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        boolean isLiked = communityCountService.isCommentLiked(commentId, userId);
        return ResponseEntity.ok(isLiked);
    }

    // 페이지네이션 (프리미엄 구독자 전용)
    @GetMapping("/posts")
    public ResponseEntity<Page<CommunityPostResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        subscriptionService.validateSubscription(userId);
        Sort.Direction sortDirection = Sort.Direction.valueOf(direction.toUpperCase());
        Pageable pageable = PageRequest.of(page, size, sortDirection, sort);
        Page<CommunityPostResponse> posts = communityPostService.getPosts(pageable);
        return ResponseEntity.ok(posts);
    }
}