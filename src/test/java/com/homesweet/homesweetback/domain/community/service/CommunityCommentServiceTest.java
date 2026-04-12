package com.homesweet.homesweetback.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.homesweet.homesweetback.common.cache.CacheHelper;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.OAuth2Provider;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.entity.UserRole;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.config.CommunityConfig;
import com.homesweet.homesweetback.domain.community.dto.CommunityCommentRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityCommentResponse;
import com.homesweet.homesweetback.domain.community.entity.CommunityCommentEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.repository.CommunityCommentRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityCommentService 단위 테스트")
class CommunityCommentServiceTest {

    private static final String CACHE_PREFIX = "comments::post::";

    @Mock
    private CommunityCommentRepository commentRepository;

    @Mock
    private CommunityPostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommunityCountService communityCountService;

    @Mock
    private CacheHelper cacheHelper;

    private CommunityConfig config;

    @InjectMocks
    private CommunityCommentService communityCommentService;

    @BeforeEach
    void setUp() {
        config = new CommunityConfig(
                new CommunityConfig.CacheConfig(Duration.ofHours(1), Duration.ofMinutes(1), Duration.ofMinutes(30)),
                null,
                null);
        communityCommentService = new CommunityCommentService(
                commentRepository,
                postRepository,
                userRepository,
                communityCountService,
                cacheHelper,
                config);
    }

    private User createUser(Long userId) {
        User user = User.builder()
                .email("user@test.com")
                .name("작성자")
                .provider(OAuth2Provider.GOOGLE)
                .role(UserRole.USER)
                .build();
        user.setId(userId);
        return user;
    }

    private CommunityPostEntity createPost(User user, Long postId) {
        return CommunityPostEntity.builder()
                .postId(postId)
                .author(user)
                .title("게시글 제목")
                .content("게시글 내용")
                .category("자유")
                .build();
    }

    private CommunityCommentEntity createComment(CommunityPostEntity post, User author, Long commentId, Long parentCommentId) {
        return CommunityCommentEntity.builder()
                .commentId(commentId)
                .post(post)
                .author(author)
                .parentCommentId(parentCommentId)
                .content("원본 댓글")
                .build();
    }

    @Nested
    @DisplayName("댓글 작성")
    class CreateComment {

        @Test
        @DisplayName("댓글을 생성할 수 있다")
        void createComment_Success() {
            Long userId = 1L;
            Long postId = 10L;
            User author = createUser(userId);
            CommunityPostEntity post = createPost(author, postId);
            CommunityCommentEntity saved = createComment(post, author, 100L, null);

            given(userRepository.findById(userId)).willReturn(Optional.of(author));
            given(postRepository.getReferenceById(postId)).willReturn(post);
            given(commentRepository.save(any(CommunityCommentEntity.class))).willReturn(saved);

            CommunityCommentResponse response = communityCommentService.createComment(
                    postId,
                    new CommunityCommentRequest("댓글 내용", null),
                    userId);

            assertThat(response.commentId()).isEqualTo(saved.getCommentId());
            assertThat(response.postId()).isEqualTo(postId);
            verify(communityCountService).increaseCommentCount(postId);
            verify(cacheHelper).deleteCache(CACHE_PREFIX + postId);
        }

        @Test
        @DisplayName("대댓글 작성 시 부모 댓글이 있으면 저장된다")
        void createComment_WithParent_Success() {
            Long userId = 1L;
            Long postId = 10L;
            User author = createUser(userId);
            CommunityPostEntity post = createPost(author, postId);
            CommunityCommentEntity parentComment = createComment(post, author, 100L, null);
            CommunityCommentEntity saved = createComment(post, author, 101L, 100L);

            given(userRepository.findById(userId)).willReturn(Optional.of(author));
            given(commentRepository.findById(parentComment.getCommentId())).willReturn(Optional.of(parentComment));
            given(postRepository.getReferenceById(postId)).willReturn(post);
            given(commentRepository.save(any(CommunityCommentEntity.class))).willReturn(saved);

            CommunityCommentResponse response = communityCommentService.createComment(
                    postId,
                    new CommunityCommentRequest("대댓글 내용", parentComment.getCommentId()),
                    userId);

            assertThat(response.parentCommentId()).isEqualTo(parentComment.getCommentId());
            verify(commentRepository).findById(parentComment.getCommentId());
        }

        @Test
        @DisplayName("대댓글의 부모 댓글이 없으면 예외가 발생한다")
        void createComment_ParentNotFound() {
            Long userId = 1L;
            Long postId = 10L;
            User author = createUser(userId);

            given(userRepository.findById(userId)).willReturn(Optional.of(author));
            given(commentRepository.findById(100L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> communityCommentService.createComment(
                    postId,
                    new CommunityCommentRequest("대댓글 내용", 100L),
                    userId))
                    .isInstanceOf(CommunityException.class)
                    .hasMessage(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND.getMessage());
            verify(commentRepository, never()).save(any(CommunityCommentEntity.class));
        }
    }

    @Nested
    @DisplayName("댓글 조회")
    class GetComments {

        @Test
        @DisplayName("캐시 히트 시 캐시값과 좋아요 수를 결합한다")
        void getComments_CacheHit() {
            Long postId = 10L;
            List<CommunityCommentResponse> cached = List.of(
                    new CommunityCommentResponse(1L, postId, 2L, "작성자", "댓글1", null, 0, false, null, null),
                    new CommunityCommentResponse(2L, postId, 3L, "작성자2", "댓글2", null, 0, false, null, null)
            );

            given(cacheHelper.getFromCache(eq(CACHE_PREFIX + postId), any(TypeReference.class)))
                    .willReturn(Optional.of(cached));
            given(communityCountService.getBulkCommentLikeCountsFromDb(List.of(1L, 2L)))
                    .willReturn(Map.of(1L, 1, 2L, 2));

            List<CommunityCommentResponse> result = communityCommentService.getCommentsByPostId(postId);

            assertThat(result.get(0).likeCount()).isEqualTo(1);
            assertThat(result.get(1).likeCount()).isEqualTo(2);
            verify(commentRepository, never()).findByPost_PostIdAndIsDeletedFalse(postId);
        }

        @Test
        @DisplayName("DB 조회 시 댓글 목록과 좋아요 수로 응답한다")
        void getComments_DbHit() {
            Long postId = 10L;
            User author = createUser(1L);
            CommunityPostEntity post = createPost(author, postId);
            CommunityCommentEntity comment1 = createComment(post, author, 1L, null);
            CommunityCommentEntity comment2 = createComment(post, author, 2L, null);

            given(cacheHelper.getFromCache(eq(CACHE_PREFIX + postId), any(TypeReference.class)))
                    .willReturn(Optional.empty());
            given(commentRepository.findByPost_PostIdAndIsDeletedFalse(postId))
                    .willReturn(List.of(comment1, comment2));
            given(communityCountService.getBulkCommentLikeCountsFromDb(List.of(1L, 2L)))
                    .willReturn(Map.of(1L, 3, 2L, 4));

            List<CommunityCommentResponse> result = communityCommentService.getCommentsByPostId(postId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).likeCount()).isEqualTo(3);
            assertThat(result.get(1).likeCount()).isEqualTo(4);
            verify(cacheHelper).setCache(eq(CACHE_PREFIX + postId), any(), eq(config.cache().commentsTtl()));
        }
    }

    @Nested
    @DisplayName("댓글 수정/삭제")
    class UpdateAndDelete {

        @Test
        @DisplayName("본인 댓글은 수정할 수 있다")
        void updateComment_Success() {
            Long userId = 1L;
            User author = createUser(userId);
            Long postId = 10L;
            CommunityPostEntity post = createPost(author, postId);
            CommunityCommentEntity comment = createComment(post, author, 100L, null);

            given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

            CommunityCommentResponse response = communityCommentService.updateComment(
                    100L,
                    new CommunityCommentRequest("수정 댓글", null),
                    userId);

            assertThat(response.content()).isEqualTo("수정 댓글");
            verify(cacheHelper).deleteCache(CACHE_PREFIX + postId);
        }

        @Test
        @DisplayName("본인 댓글이 아니면 수정할 수 없다")
        void updateComment_Forbidden() {
            User owner = createUser(10L);
            User another = createUser(20L);
            Long postId = 10L;
            CommunityPostEntity post = createPost(owner, postId);
            CommunityCommentEntity comment = createComment(post, owner, 100L, null);

            given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

            assertThatThrownBy(() -> communityCommentService.updateComment(100L,
                    new CommunityCommentRequest("수정 댓글", null),
                    another.getId()))
                    .isInstanceOf(CommunityException.class)
                    .hasMessage(ErrorCode.COMMUNITY_COMMENT_FORBIDDEN.getMessage());

            verify(cacheHelper, never()).deleteCache(CACHE_PREFIX + postId);
        }

        @Test
        @DisplayName("본인 댓글을 삭제(소프트 삭제)할 수 있다")
        void deleteComment_Success() {
            User author = createUser(1L);
            Long postId = 10L;
            CommunityPostEntity post = createPost(author, postId);
            CommunityCommentEntity comment = createComment(post, author, 100L, null);

            given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

            communityCommentService.deleteComment(100L, postId, author.getId());

            assertThat(comment.getIsDeleted()).isTrue();
            verify(communityCountService).decreaseCommentCount(postId);
            verify(cacheHelper).deleteCache(CACHE_PREFIX + postId);
        }
    }
}
