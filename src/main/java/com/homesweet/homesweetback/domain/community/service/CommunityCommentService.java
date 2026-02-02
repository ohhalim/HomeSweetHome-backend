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

/**
 * [커뮤니티 댓글 서비스 - 댓글 CRUD 담당]
 *
 * [이 서비스가 하는 일]
 * 1. 댓글 작성 (대댓글 포함)
 * 2. 댓글 목록 조회 (게시글별)
 * 3. 댓글 수정
 * 4. 댓글 삭제 (소프트 삭제)
 *
 * [대댓글이란?]
 * 댓글에 달린 댓글. parentCommentId로 부모 댓글을 참조해.
 * 예: "좋은 글이네요" <- 원댓글
 * ㄴ "저도 그렇게 생각해요" <- 대댓글 (parentCommentId = 원댓글 ID)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본: 읽기 전용 트랜잭션
public class CommunityCommentService {

        // 댓글 DB 접근용
        private final CommunityCommentRepository commentRepository;
        // 게시글 참조용 (댓글은 게시글에 속하니까)
        private final CommunityPostRepository postRepository;
        // 작성자 정보 조회용
        private final UserRepository userRepository;
        // 댓글수, 좋아요수 관리 (Redis)
        private final CommunityCountService communityCountService;
        // 캐시 읽기/쓰기 헬퍼
        private final CacheHelper cacheHelper;
        // 설정값 (캐시 TTL 등)
        private final CommunityConfig config;

        // 댓글 캐시 키 접두어: "comments::post::123" (게시글 123의 댓글들)
        private static final String COMMENTS_CACHE_PREFIX = "comments::post::";

        /**
         * [댓글 작성]
         *
         * [하는 일]
         * 1. 작성자 정보 조회
         * 2. 게시글의 댓글수 +1 (Redis에서 관리)
         * 3. 대댓글이면 부모 댓글이 존재하는지 확인
         * 4. 댓글 DB에 저장
         * 5. 댓글 캐시 삭제 (새 댓글이 추가됐으니까)
         *
         * @param postId  댓글 달 게시글 ID
         * @param request 댓글 내용 + 부모댓글ID (대댓글인 경우)
         * @param userId  작성자 ID
         */
        @Transactional
        public CommunityCommentResponse createComment(Long postId, CommunityCommentRequest request, Long userId) {
                // 1. 작성자 정보 가져오기
                User author = userRepository.findById(userId)
                                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

                // 2. 게시글의 댓글수 증가 (Redis)
                communityCountService.increaseCommentCount(postId);

                // 3. 대댓글인 경우 부모 댓글이 있는지 확인
                if (request.parentCommentId() != null) {
                        commentRepository.findById(request.parentCommentId())
                                        .orElseThrow(() -> new CommunityException(
                                                        ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
                }

                // 4. 게시글 참조 가져오기 (Lazy Loading용 - 실제 DB 조회는 필요할 때만)
                CommunityPostEntity post = postRepository.getReferenceById(postId);

                // 5. 댓글 엔티티 생성 및 저장
                CommunityCommentEntity comment = CommunityCommentEntity.builder()
                                .post(post)
                                .author(author)
                                .content(request.content())
                                .parentCommentId(request.parentCommentId()) // 대댓글이면 부모 ID, 아니면 null
                                .build();

                CommunityCommentEntity savedComment = commentRepository.save(comment);

                // 6. 이 게시글의 댓글 캐시 삭제 (새 댓글 추가됐으니)
                invalidateCommentsCache(postId);

                return CommunityCommentResponse.from(savedComment);
        }

        /**
         * [게시글의 댓글 목록 조회 - Cache-Aside 패턴]
         *
         * [동작 흐름]
         * 1. 캐시에서 먼저 찾기
         * 2. 있으면 -> 각 댓글의 최신 좋아요수와 합쳐서 반환
         * 3. 없으면 -> DB에서 조회 -> 캐시에 저장 -> 반환
         *
         * @param postId 게시글 ID
         * @return 해당 게시글의 모든 댓글 (대댓글 포함)
         */
        public List<CommunityCommentResponse> getCommentsByPostId(Long postId) {
                String cacheKey = COMMENTS_CACHE_PREFIX + postId;

                // 1. 캐시에서 조회
                Optional<List<CommunityCommentResponse>> cached = cacheHelper.getFromCache(
                                cacheKey, new TypeReference<List<CommunityCommentResponse>>() {
                                });

                if (cached.isPresent()) {
                        // 캐시 히트! 각 댓글의 최신 좋아요수 가져와서 합치기
                        List<Long> commentIds = cached.get().stream()
                                        .map(CommunityCommentResponse::commentId).toList();
                        // 여러 댓글의 좋아요수를 한 번에 조회 (N+1 방지)
                        Map<Long, Integer> likeCounts = communityCountService
                                        .getBulkCommentLikeCountsFromCache(commentIds);

                        // 각 댓글에 최신 좋아요수 적용
                        return cached.get().stream()
                                        .map(comment -> withUpdatedLikeCount(comment,
                                                        likeCounts.getOrDefault(comment.commentId(), 0)))
                                        .toList();
                }

                // 2. 캐시 미스 -> DB에서 조회
                List<CommunityCommentEntity> comments = commentRepository.findByPost_PostIdAndIsDeletedFalse(postId);

                if (comments.isEmpty()) {
                        return List.of(); // 빈 리스트 반환
                }

                // 좋아요수 일괄 조회 (N+1 방지)
                List<Long> commentIds = comments.stream()
                                .map(CommunityCommentEntity::getCommentId).toList();
                Map<Long, Integer> likeCounts = communityCountService.getBulkCommentLikeCountsFromCache(commentIds);

                // 응답 객체 리스트 생성
                List<CommunityCommentResponse> responses = comments.stream()
                                .map(comment -> CommunityCommentResponse.fromWithCachedLikeCount(
                                                comment, likeCounts.getOrDefault(comment.getCommentId(), 0)))
                                .toList();

                // 3. 캐시에 저장
                cacheHelper.setCache(cacheKey, responses, config.cache().commentsTtl());

                return responses;
        }

        /**
         * [댓글 수정]
         *
         * [하는 일]
         * 1. 댓글 존재 확인
         * 2. 본인 댓글인지 확인 (아니면 403)
         * 3. 내용 수정
         * 4. 캐시 삭제
         */
        @Transactional
        public CommunityCommentResponse updateComment(Long commentId, CommunityCommentRequest request, Long userId) {
                // 1. 댓글 조회
                CommunityCommentEntity comment = commentRepository.findById(commentId)
                                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

                // 2. 본인 댓글인지 확인
                if (!comment.isAuthor(userId)) {
                        throw new CommunityException(ErrorCode.COMMUNITY_COMMENT_FORBIDDEN);
                }

                // 3. 내용 수정
                comment.updateComment(request.content());

                // 4. 캐시 삭제
                invalidateCommentsCache(comment.getPost().getPostId());

                return CommunityCommentResponse.from(comment);
        }

        /**
         * [댓글 삭제 - 소프트 삭제]
         *
         * [하는 일]
         * 1. 댓글 존재 확인
         * 2. 본인 댓글인지 확인
         * 3. isDeleted = true 로 변경
         * 4. 게시글의 댓글수 -1
         * 5. 캐시 삭제
         */
        @Transactional
        public void deleteComment(Long commentId, Long postId, Long userId) {
                CommunityCommentEntity comment = commentRepository.findById(commentId)
                                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

                if (!comment.isAuthor(userId)) {
                        throw new CommunityException(ErrorCode.COMMUNITY_COMMENT_FORBIDDEN);
                }

                // 소프트 삭제
                comment.deleteComment();
                // 게시글의 댓글수 감소
                communityCountService.decreaseCommentCount(postId);

                invalidateCommentsCache(postId);
        }

        // ============================================================
        // [내부 헬퍼 메서드들]
        // ============================================================

        /**
         * [캐시된 댓글에 최신 좋아요수 적용]
         */
        private CommunityCommentResponse withUpdatedLikeCount(CommunityCommentResponse cached, Integer likeCount) {
                return new CommunityCommentResponse(
                                cached.commentId(), cached.postId(), cached.authorId(),
                                cached.authorName(), cached.content(),
                                cached.parentCommentId(),
                                likeCount, // 최신 좋아요수로 교체
                                cached.isModified(), cached.createdAt(), cached.modifiedAt());
        }

        /**
         * [댓글 캐시 삭제]
         * 댓글이 추가/수정/삭제되면 해당 게시글의 댓글 캐시를 지워야 해.
         */
        private void invalidateCommentsCache(Long postId) {
                cacheHelper.deleteCache(COMMENTS_CACHE_PREFIX + postId);
                log.debug("Cache invalidated for comments of post: {}", postId);
        }
}