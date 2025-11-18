package com.homesweet.homesweetback.domain.community.controller;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2UserPrincipal;
import com.homesweet.homesweetback.domain.community.dto.VoteRequest;
import com.homesweet.homesweetback.domain.community.dto.VoteResponse;
import com.homesweet.homesweetback.domain.community.entity.CommentVoteEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityCommentEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.entity.PostVoteEntity;
import com.homesweet.homesweetback.domain.community.repository.CommunityCommentRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import com.homesweet.homesweetback.domain.community.service.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Vote Controller
 *
 * Reddit-style Upvote/Downvote API
 *
 * @author ohhalim777@gmail.com
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Vote", description = "투표 (Upvote/Downvote) API")
public class VoteController {

    private final VoteService voteService;
    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;

    /**
     * 게시글 투표
     */
    @PostMapping("/posts/{postId}/vote")
    @Operation(
        summary = "게시글 투표",
        description = "게시글에 Upvote 또는 Downvote를 합니다. 같은 투표를 다시 하면 취소됩니다."
    )
    public ResponseEntity<VoteResponse> votePost(
            @PathVariable @Parameter(description = "게시글 ID") Long postId,
            @RequestBody @Valid VoteRequest request,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        // 투표 처리
        voteService.togglePostVote(postId, userId, request.getVoteType());

        // 최신 투표 상태 조회
        PostVoteEntity.VoteType currentVote = voteService.getPostVoteStatus(postId, userId);
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow();

        VoteResponse response = VoteResponse.of(
                currentVote,
                post.getUpvoteCount(),
                post.getDownvoteCount(),
                post.getScore()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 댓글 투표
     */
    @PostMapping("/comments/{commentId}/vote")
    @Operation(
        summary = "댓글 투표",
        description = "댓글에 Upvote 또는 Downvote를 합니다. 같은 투표를 다시 하면 취소됩니다."
    )
    public ResponseEntity<VoteResponse> voteComment(
            @PathVariable @Parameter(description = "댓글 ID") Long commentId,
            @RequestBody @Valid VoteRequest request,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        // 댓글 투표는 CommentVoteEntity.VoteType 사용
        CommentVoteEntity.VoteType voteType = request.getVoteType() == PostVoteEntity.VoteType.UPVOTE ?
                CommentVoteEntity.VoteType.UPVOTE : CommentVoteEntity.VoteType.DOWNVOTE;

        // 투표 처리
        voteService.toggleCommentVote(commentId, userId, voteType);

        // 최신 투표 상태 조회
        CommentVoteEntity.VoteType currentVote = voteService.getCommentVoteStatus(commentId, userId);
        CommunityCommentEntity comment = commentRepository.findByCommentIdAndIsDeletedFalse(commentId)
                .orElseThrow();

        // CommentVoteEntity.VoteType → PostVoteEntity.VoteType 변환 (응답용)
        PostVoteEntity.VoteType responseVoteType = currentVote == null ? null :
                (currentVote == CommentVoteEntity.VoteType.UPVOTE ?
                        PostVoteEntity.VoteType.UPVOTE : PostVoteEntity.VoteType.DOWNVOTE);

        VoteResponse response = VoteResponse.of(
                responseVoteType,
                comment.getUpvoteCount(),
                comment.getDownvoteCount(),
                comment.getScore()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 게시글 투표 상태 조회
     */
    @GetMapping("/posts/{postId}/vote")
    @Operation(summary = "게시글 투표 상태 조회", description = "현재 사용자의 게시글 투표 상태를 조회합니다")
    public ResponseEntity<VoteResponse> getPostVoteStatus(
            @PathVariable @Parameter(description = "게시글 ID") Long postId,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        PostVoteEntity.VoteType currentVote = voteService.getPostVoteStatus(postId, userId);
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow();

        VoteResponse response = VoteResponse.of(
                currentVote,
                post.getUpvoteCount(),
                post.getDownvoteCount(),
                post.getScore()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 댓글 투표 상태 조회
     */
    @GetMapping("/comments/{commentId}/vote")
    @Operation(summary = "댓글 투표 상태 조회", description = "현재 사용자의 댓글 투표 상태를 조회합니다")
    public ResponseEntity<VoteResponse> getCommentVoteStatus(
            @PathVariable @Parameter(description = "댓글 ID") Long commentId,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        CommentVoteEntity.VoteType currentVote = voteService.getCommentVoteStatus(commentId, userId);
        CommunityCommentEntity comment = commentRepository.findByCommentIdAndIsDeletedFalse(commentId)
                .orElseThrow();

        // CommentVoteEntity.VoteType → PostVoteEntity.VoteType 변환
        PostVoteEntity.VoteType responseVoteType = currentVote == null ? null :
                (currentVote == CommentVoteEntity.VoteType.UPVOTE ?
                        PostVoteEntity.VoteType.UPVOTE : PostVoteEntity.VoteType.DOWNVOTE);

        VoteResponse response = VoteResponse.of(
                responseVoteType,
                comment.getUpvoteCount(),
                comment.getDownvoteCount(),
                comment.getScore()
        );

        return ResponseEntity.ok(response);
    }
}
