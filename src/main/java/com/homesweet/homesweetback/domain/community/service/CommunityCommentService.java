package com.homesweet.homesweetback.domain.community.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.CommunityCommentRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityCommentResponse;
import com.homesweet.homesweetback.domain.community.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.entity.CommunityCommentEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.repository.CommunityCommentRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import com.homesweet.homesweetback.domain.notification.domain.notification.CommunityNotification;
import com.homesweet.homesweetback.domain.notification.service.NotificationSendService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCommentService {
    private final CommunityCommentRepository commentRepository;
    private final CommunityPostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationSendService notificationSendService;
    private final CommunityCountService communityCountService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String COMMENTS_CACHE_PREFIX = "comments::post::";
    private static final Duration COMMENTS_CACHE_TTL = Duration.ofMinutes(30);

    /**
     * 댓글 작성
     */
    @Transactional
    // 사용자 검증
    public CommunityCommentResponse createComment(Long postId, CommunityCommentRequest request, Long userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        communityCountService.increaseCommentCount(postId);

        // 2. 대댓글인 경우 부모 댓글 존재 여부 확인
        if (request.parentCommentId() != null) {
            commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
        }

        // 3. 게시글 프록시 참조 (FK 설정용)
        CommunityPostEntity post = postRepository.getReferenceById(postId);

        // 4. 댓글 엔티티 생성 및 연결
        CommunityCommentEntity comment = CommunityCommentEntity.builder()
                .post(post)
                .author(author)
                .content(request.content())
                .parentCommentId((request.parentCommentId()))
                .build();

        // 5. 댓글 저장 (FK constraint 검증으로 S lock 발생하지만, 이미 X lock 획득 완료)
        CommunityCommentEntity savedComment = commentRepository.save(comment);

        // TODO: 알림 전송 - 트랜잭션 롤백 이슈로 인해 임시 주석 처리
        // notificationSendService.sendTemplateNotificationToSingleUser(
        //         post.getAuthor().getId(),
        //         CommunityNotification.NewComment.builder()
        //                 .userName(author.getName())
        //                 .postId(post.getPostId())
        //                 .postTitle(post.getTitle())
        //                 .build());


        // 캐시 무효화
        stringRedisTemplate.delete(COMMENTS_CACHE_PREFIX + postId);
        log.debug("Cache invalidated for comments of post: {}", postId);

        return CommunityCommentResponse.from(savedComment);
    }

    /**
     * 해당 게시글의 모든 댓글 조회 - Cache-Aside 패턴 적용
     */
    public List<CommunityCommentResponse> getCommentsByPostId(Long postId) {
        String cacheKey = COMMENTS_CACHE_PREFIX + postId;

        // 1. 캐시 조회
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                List<CommunityCommentResponse> cached = objectMapper.readValue(
                        cachedJson, new TypeReference<List<CommunityCommentResponse>>() {});
                
                // 좋아요 수는 항상 최신값으로 조회
                return cached.stream()
                        .map(comment -> new CommunityCommentResponse(
                                comment.commentId(), comment.postId(), comment.authorId(),
                                comment.authorName(), comment.content(),
                                comment.parentCommentId(),
                                communityCountService.getCommentLikeCountFromCache(comment.commentId()),
                                comment.isModified(), comment.createdAt(), comment.modifiedAt()
                        ))
                        .toList();
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached comments for post: {}", postId, e);
                stringRedisTemplate.delete(cacheKey);
            }
        }

        // 2. Cache Miss - DB 조회
        List<CommunityCommentEntity> comments =
                commentRepository.findByPost_PostIdAndIsDeletedFalse(postId);

        List<CommunityCommentResponse> responses = comments.stream()
                .map(comment -> {
                    Integer likeCount = communityCountService.getCommentLikeCountFromCache(comment.getCommentId());
                    return CommunityCommentResponse.fromWithCachedLikeCount(comment, likeCount);
                })
                .toList();

        // 3. 캐시 저장
        try {
            String json = objectMapper.writeValueAsString(responses);
            stringRedisTemplate.opsForValue().set(cacheKey, json, COMMENTS_CACHE_TTL);
            log.debug("Cached comments for post: {}", postId);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache comments for post: {}", postId, e);
        }

        return responses;
    }

    /**
     * 댓글 수정
     */
    @Transactional
    public CommunityCommentResponse updateComment(Long commentId, CommunityCommentRequest request, Long userId) {
        // 댓글 조회
        CommunityCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

        // 작성자 본인 확인
        if (!comment.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_COMMENT_FORBIDDEN);
        }

        // 댓글 수정
        comment.updateComment(request.content());

        // 캐시 무효화
        stringRedisTemplate.delete(COMMENTS_CACHE_PREFIX + comment.getPost().getPostId());
        log.debug("Cache invalidated for comments of post: {}", comment.getPost().getPostId());

        return CommunityCommentResponse.from(comment);
    }

    /**
     * 댓글 삭제
     */
    @Transactional
    public void deleteComment(Long commentId, Long postId, Long userId) {
        // 댓글 조회
        CommunityCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

        // 작성자 본인 확인
        if (!comment.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_COMMENT_FORBIDDEN);
        }

        // 댓글 소프트 삭제
        comment.deleteComment();

        // 게시글의 댓글 수 감소
        communityCountService.decreaseCommentCount(postId);

        // 캐시 무효화
        stringRedisTemplate.delete(COMMENTS_CACHE_PREFIX + postId);
        log.debug("Cache invalidated for comments of post: {}", postId);
    }
}