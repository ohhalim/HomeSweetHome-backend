package com.homesweet.homesweetback.domain.community.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.homesweet.homesweetback.common.cache.CacheHelper;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.config.CommunityConfig;
import com.homesweet.homesweetback.domain.community.dto.CommunityCommentRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityCommentResponse;
import com.homesweet.homesweetback.domain.community.entity.CommunityCommentEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.repository.CommunityCommentRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCommentService {

    private final CommunityCommentRepository commentRepository;
    private final CommunityPostRepository postRepository;
    private final UserRepository userRepository;
    private final CommunityCountService communityCountService;
    private final CacheHelper cacheHelper;
    private final CommunityConfig config;

    private static final String COMMENTS_CACHE_PREFIX = "comments::post::";

    /**
     * 댓글 작성
     */
    @Transactional
    public CommunityCommentResponse createComment(Long postId, CommunityCommentRequest request, Long userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        communityCountService.increaseCommentCount(postId);

        // 대댓글인 경우 부모 댓글 존재 여부 확인
        if (request.parentCommentId() != null) {
            commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
        }

        CommunityPostEntity post = postRepository.getReferenceById(postId);

        CommunityCommentEntity comment = CommunityCommentEntity.builder()
                .post(post)
                .author(author)
                .content(request.content())
                .parentCommentId(request.parentCommentId())
                .build();

        CommunityCommentEntity savedComment = commentRepository.save(comment);

        invalidateCommentsCache(postId);

        return CommunityCommentResponse.from(savedComment);
    }

    /**
     * 해당 게시글의 모든 댓글 조회 - Cache-Aside 패턴
     */
    public List<CommunityCommentResponse> getCommentsByPostId(Long postId) {
        String cacheKey = COMMENTS_CACHE_PREFIX + postId;

        // 1. 캐시 조회
        Optional<List<CommunityCommentResponse>> cached = cacheHelper.getFromCache(
                cacheKey, new TypeReference<List<CommunityCommentResponse>>() {
                });

        if (cached.isPresent()) {
            List<Long> commentIds = cached.get().stream()
                    .map(CommunityCommentResponse::commentId).toList();
            Map<Long, Integer> likeCounts = communityCountService.getBulkCommentLikeCountsFromCache(commentIds);

            return cached.get().stream()
                    .map(comment -> withUpdatedLikeCount(comment,
                            likeCounts.getOrDefault(comment.commentId(), 0)))
                    .toList();
        }

        // 2. Cache Miss - DB 조회
        List<CommunityCommentEntity> comments = commentRepository.findByPost_PostIdAndIsDeletedFalse(postId);

        if (comments.isEmpty()) {
            return List.of();
        }

        // Bulk 좋아요수 조회
        List<Long> commentIds = comments.stream()
                .map(CommunityCommentEntity::getCommentId).toList();
        Map<Long, Integer> likeCounts = communityCountService.getBulkCommentLikeCountsFromCache(commentIds);

        List<CommunityCommentResponse> responses = comments.stream()
                .map(comment -> CommunityCommentResponse.fromWithCachedLikeCount(
                        comment, likeCounts.getOrDefault(comment.getCommentId(), 0)))
                .toList();

        // 3. 캐시 저장
        cacheHelper.setCache(cacheKey, responses, config.cache().commentsTtl());

        return responses;
    }

    /**
     * 댓글 수정
     */
    @Transactional
    public CommunityCommentResponse updateComment(Long commentId, CommunityCommentRequest request, Long userId) {
        CommunityCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

        if (!comment.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_COMMENT_FORBIDDEN);
        }

        comment.updateComment(request.content());

        invalidateCommentsCache(comment.getPost().getPostId());

        return CommunityCommentResponse.from(comment);
    }

    /**
     * 댓글 삭제
     */
    @Transactional
    public void deleteComment(Long commentId, Long postId, Long userId) {
        CommunityCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

        if (!comment.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_COMMENT_FORBIDDEN);
        }

        comment.deleteComment();
        communityCountService.decreaseCommentCount(postId);

        invalidateCommentsCache(postId);
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================

    private CommunityCommentResponse withUpdatedLikeCount(CommunityCommentResponse cached, Integer likeCount) {
        return new CommunityCommentResponse(
                cached.commentId(), cached.postId(), cached.authorId(),
                cached.authorName(), cached.content(),
                cached.parentCommentId(),
                likeCount,
                cached.isModified(), cached.createdAt(), cached.modifiedAt());
    }

    private void invalidateCommentsCache(Long postId) {
        cacheHelper.deleteCache(COMMENTS_CACHE_PREFIX + postId);
        log.debug("Cache invalidated for comments of post: {}", postId);
    }
}