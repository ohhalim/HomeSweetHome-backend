package com.homesweet.homesweetback.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.homesweet.homesweetback.common.cache.CacheHelper;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.OAuth2Provider;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.entity.UserRole;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.config.CommunityConfig;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostResponse;
import com.homesweet.homesweetback.domain.community.dto.PostCounts;
import com.homesweet.homesweetback.domain.community.entity.CommunityImageEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.repository.CommunityImageRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityPostService 단위 테스트")
class CommunityPostServiceTest {

    private static final String CACHE_PREFIX = "communityPost::";
    private static final String LIST_CACHE_PREFIX = "communityPostList::";

    @Mock
    private CommunityPostRepository postRepository;

    @Mock
    private CommunityImageRepository imageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommunityImageUploader imageUploader;

    @Mock
    private CommunityCountService communityCountService;

    @Mock
    private CacheHelper cacheHelper;

    private CommunityConfig config;

    @InjectMocks
    private CommunityPostService communityPostService;

    @BeforeEach
    void setUp() {
        config = new CommunityConfig(
                new CommunityConfig.CacheConfig(Duration.ofHours(1), Duration.ofMinutes(1), Duration.ofMinutes(30)),
                null,
                null);

        communityPostService = new CommunityPostService(
                postRepository,
                imageRepository,
                userRepository,
                imageUploader,
                communityCountService,
                cacheHelper,
                config);
    }

    private User createUser(Long userId) {
        return User.builder()
                .email("writer@test.com")
                .name("작성자")
                .provider(OAuth2Provider.GOOGLE)
                .role(UserRole.USER)
                .build();
    }

    private CommunityPostEntity createPost(User author, Long postId) {
        return CommunityPostEntity.builder()
                .postId(postId)
                .author(author)
                .title("테스트 제목")
                .content("테스트 내용")
                .category("자유")
                .build();
    }

    private CommunityImageEntity createImage(CommunityPostEntity post, String url, int order) {
        return CommunityImageEntity.builder()
                .post(post)
                .imageUrl(url)
                .imageOrder(order)
                .build();
    }

    // ===== 게시글 작성 =====

    @Nested
    @DisplayName("게시글 작성")
    class CreatePost {

        @Test
        @DisplayName("이미지 없이 게시글을 작성할 수 있다")
        void createPost_withoutImages_Success() {
            Long userId = 1L;
            CommunityPostRequest request = new CommunityPostRequest("제목", "내용", "자유");
            User author = createUser(userId);
            author.setId(userId);
            CommunityPostEntity saved = createPost(author, 10L);

            given(userRepository.findById(userId)).willReturn(Optional.of(author));
            given(postRepository.save(any(CommunityPostEntity.class))).willReturn(saved);

            CommunityPostResponse response = communityPostService.createPost(null, request, userId);

            assertThat(response.postId()).isEqualTo(saved.getPostId());
            assertThat(response.authorId()).isEqualTo(userId);
            assertThat(response.title()).isEqualTo("테스트 제목");
            assertThat(response.imagesUrl()).isEmpty();
            verify(imageUploader, never()).uploadCommunityImages(anyList());
            verify(cacheHelper).invalidateCacheByPattern(LIST_CACHE_PREFIX + "*");
        }

        @Test
        @DisplayName("이미지와 함께 게시글을 작성할 수 있다")
        void createPost_withImages_Success() {
            Long userId = 1L;
            CommunityPostRequest request = new CommunityPostRequest("제목", "내용", "자유");
            User author = createUser(userId);
            author.setId(userId);
            List<MultipartFile> images = List.of(
                    org.mockito.Mockito.mock(MultipartFile.class),
                    org.mockito.Mockito.mock(MultipartFile.class));

            CommunityPostEntity saved = createPost(author, 20L);

            given(userRepository.findById(userId)).willReturn(Optional.of(author));
            given(postRepository.save(any(CommunityPostEntity.class))).willReturn(saved);
            given(imageUploader.uploadCommunityImages(images)).willReturn(List.of("url-1", "url-2"));

            CommunityPostResponse response = communityPostService.createPost(images, request, userId);

            assertThat(response.postId()).isEqualTo(saved.getPostId());
            assertThat(response.imagesUrl()).containsExactly("url-1", "url-2");
            verify(imageRepository, times(2)).save(any(CommunityImageEntity.class));
            verify(imageUploader).uploadCommunityImages(images);
        }

        @Test
        @DisplayName("작성자 조회 실패 시 예외가 발생한다")
        void createPost_UserNotFound() {
            Long userId = 999L;
            CommunityPostRequest request = new CommunityPostRequest("제목", "내용", "자유");

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> communityPostService.createPost(List.of(), request, userId))
                    .isInstanceOf(CommunityException.class)
                    .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
            verify(postRepository, never()).save(any(CommunityPostEntity.class));
        }
    }

    // ===== 게시글 조회 =====

    @Nested
    @DisplayName("게시글 조회")
    class GetPost {

        @Test
        @DisplayName("캐시 히트 시 캐시값과 최신 카운트를 조합해 반환한다")
        void getPost_CacheHit_ReturnsUpdatedCounts() {
            CommunityPostResponse cached = new CommunityPostResponse(
                    1L,
                    10L,
                    "작성자",
                    "캐시 제목",
                    "캐시 내용",
                    "자유",
                    0,
                    0,
                    0,
                    false,
                    null,
                    null,
                    List.of("cache-url"));

            given(cacheHelper.getFromCache(CACHE_PREFIX + "1", CommunityPostResponse.class))
                    .willReturn(Optional.of(cached));
            given(communityCountService.getPostCounts(1L)).willReturn(new PostCounts(12, 3, 4));

            CommunityPostResponse response = communityPostService.getPost(1L);

            assertThat(response.postId()).isEqualTo(1L);
            assertThat(response.viewCount()).isEqualTo(12);
            assertThat(response.likeCount()).isEqualTo(3);
            assertThat(response.commentCount()).isEqualTo(4);
            assertThat(response.imagesUrl()).containsExactly("cache-url");
            verify(postRepository, never()).findByPostIdAndIsDeletedFalse(anyLong());
        }

        @Test
        @DisplayName("DB 조회 결과로 게시글을 반환하고 캐시에 저장한다")
        void getPost_DbHit_SaveCache() {
            Long postId = 1L;
            User author = createUser(10L);
            author.setId(10L);
            CommunityPostEntity post = createPost(author, postId);
            List<CommunityImageEntity> images = List.of(
                    createImage(post, "img-1", 1),
                    createImage(post, "img-2", 2));

            given(cacheHelper.getFromCache(CACHE_PREFIX + postId, CommunityPostResponse.class))
                    .willReturn(Optional.empty());
            given(postRepository.findByPostIdAndIsDeletedFalse(postId)).willReturn(Optional.of(post));
            given(imageRepository.findByPostOrderByImageOrderAsc(post)).willReturn(images);
            given(communityCountService.getPostCounts(postId)).willReturn(new PostCounts(1, 2, 3));

            CommunityPostResponse response = communityPostService.getPost(postId);

            assertThat(response.postId()).isEqualTo(postId);
            assertThat(response.viewCount()).isEqualTo(1);
            assertThat(response.imagesUrl()).containsExactly("img-1", "img-2");
            verify(cacheHelper).setCache(eq(CACHE_PREFIX + postId), any(CommunityPostResponse.class),
                    eq(config.cache().postTtl()));
        }

        @Test
        @DisplayName("게시글이 없으면 예외가 발생한다")
        void getPost_NotFound() {
            given(cacheHelper.getFromCache(CACHE_PREFIX + "1", CommunityPostResponse.class))
                    .willReturn(Optional.empty());
            given(postRepository.findByPostIdAndIsDeletedFalse(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> communityPostService.getPost(1L))
                    .isInstanceOf(CommunityException.class)
                    .hasMessage(ErrorCode.COMMUNITY_POST_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("게시글 목록 조회")
    class GetPosts {

        @Test
        @DisplayName("캐시 히트 시 목록 조회 응답을 최신 카운터로 보정한다")
        void getPosts_CacheHit_ReturnsUpdatedCounts() {
            Pageable pageable = PageRequest.of(0, 10);

            List<CommunityPostResponse> cached = List.of(
                    new CommunityPostResponse(1L, 10L, "작성자1", "제목1", "내용1", "자유",
                            0, 0, 0, false, null, null, List.of()),
                    new CommunityPostResponse(2L, 10L, "작성자2", "제목2", "내용2", "자유",
                            0, 0, 0, false, null, null, List.of())
            );

            given(cacheHelper.getFromCache(eq("communityPostList::0:10"), any(TypeReference.class)))
                    .willReturn(Optional.of(cached));
            given(postRepository.countByIsDeletedFalse()).willReturn(2L);
            given(communityCountService.getBulkPostCounts(List.of(1L, 2L)))
                    .willReturn(Map.of(
                            1L, new PostCounts(11, 1, 2),
                            2L, new PostCounts(22, 3, 4)));

            Page<CommunityPostResponse> result = communityPostService.getPosts(pageable);

            assertThat(result.getTotalElements()).isEqualTo(2L);
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).viewCount()).isEqualTo(11);
            assertThat(result.getContent().get(1).viewCount()).isEqualTo(22);
            verify(postRepository, never()).findByIsDeletedFalse(pageable);
            verify(cacheHelper, never()).setCache(eq("communityPostList::0:10"), any(), eq(config.cache().listTtl()));
        }

        @Test
        @DisplayName("DB 조회 시 이미지 조회와 카운터 조회 결과로 응답을 구성한다")
        void getPosts_DbHit_MapsImagesAndCounts() {
            Pageable pageable = PageRequest.of(0, 10);

            User author = createUser(10L);
            author.setId(10L);
            CommunityPostEntity post = createPost(author, 1L);
            Page<CommunityPostEntity> posts = new PageImpl<>(List.of(post), pageable, 1);
            List<CommunityImageEntity> images = List.of(createImage(post, "img-1", 1));

            given(cacheHelper.getFromCache(eq("communityPostList::0:10"), any(TypeReference.class)))
                    .willReturn(Optional.empty());
            given(postRepository.findByIsDeletedFalse(pageable)).willReturn(posts);
            given(imageRepository.findAllByPostInOrderByPostPostIdAscImageOrderAsc(anyList())).willReturn(images);
            given(communityCountService.getBulkPostCounts(List.of(1L))).willReturn(Map.of(1L, new PostCounts(3, 4, 5)));

            Page<CommunityPostResponse> result = communityPostService.getPosts(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).extracting(CommunityPostResponse::imagesUrl)
                    .containsExactly(List.of("img-1"));
            assertThat(result.getContent().get(0).viewCount()).isEqualTo(3);
            verify(cacheHelper).setCache("communityPostList::0:10", result.getContent(), config.cache().listTtl());
        }
    }

    @Nested
    @DisplayName("수정/삭제")
    class UpdateAndDelete {

        @Test
        @DisplayName("본인 글은 수정할 수 있다")
        void updatePost_Success() {
            User author = createUser(1L);
            author.setId(1L);
            CommunityPostEntity post = createPost(author, 1L);
            CommunityPostRequest request = new CommunityPostRequest("수정 제목", "수정 내용", "자유");

            given(postRepository.findByPostIdAndIsDeletedFalse(1L)).willReturn(Optional.of(post));

            CommunityPostResponse response = communityPostService.updatePost(1L, request, 1L);

            assertThat(response.title()).isEqualTo("수정 제목");
            assertThat(response.content()).isEqualTo("수정 내용");
            verify(cacheHelper).deleteCache(CACHE_PREFIX + 1L);
            verify(cacheHelper).invalidateCacheByPattern(LIST_CACHE_PREFIX + "*");
        }

        @Test
        @DisplayName("본인 글이 아니면 수정할 수 없다")
        void updatePost_Forbidden() {
            User author = createUser(1L);
            author.setId(1L);
            CommunityPostEntity post = createPost(author, 1L);
            CommunityPostRequest request = new CommunityPostRequest("수정 제목", "수정 내용", "자유");

            given(postRepository.findByPostIdAndIsDeletedFalse(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> communityPostService.updatePost(1L, request, 2L))
                    .isInstanceOf(CommunityException.class)
                    .hasMessage(ErrorCode.COMMUNITY_POST_FORBIDDEN.getMessage());
        }

        @Test
        @DisplayName("작성자는 글을 삭제할 수 있다")
        void deletePost_Success() {
            User author = createUser(1L);
            author.setId(1L);
            CommunityPostEntity post = createPost(author, 1L);

            given(postRepository.findByPostIdAndIsDeletedFalse(1L)).willReturn(Optional.of(post));

            communityPostService.deletePost(1L, 1L);

            assertThat(post.getIsDeleted()).isTrue();
            verify(cacheHelper).deleteCache(CACHE_PREFIX + 1L);
            verify(cacheHelper).invalidateCacheByPattern(LIST_CACHE_PREFIX + "*");
        }
    }
}
