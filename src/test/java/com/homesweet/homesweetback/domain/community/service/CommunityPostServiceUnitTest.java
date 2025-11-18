package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostResponse;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.event.PostCreatedEvent;
import com.homesweet.homesweetback.domain.community.repository.CommunityImageRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * CommunityPostService 단위 테스트
 *
 * @author ohhalim777@gmail.com
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("커뮤니티 게시글 서비스 단위 테스트")
class CommunityPostServiceUnitTest {

    @Mock
    private CommunityPostRepository postRepository;

    @Mock
    private CommunityImageRepository imageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommunityImageUploader imageUploader;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CommunityPostService communityPostService;

    private User testUser;
    private CommunityPostEntity testPost;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@test.com")
                .build();

        testPost = CommunityPostEntity.builder()
                .postId(1L)
                .author(testUser)
                .title("Test Title")
                .content("Test Content")
                .category("FREE")
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .build();
    }

    @Test
    @DisplayName("게시글 생성 시 이벤트가 발행된다")
    void createPost_PublishesEvent() {
        // Given
        CommunityPostRequest request = new CommunityPostRequest("Test Title", "Test Content", "FREE");
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));
        when(postRepository.save(any(CommunityPostEntity.class))).thenReturn(testPost);

        // When
        CommunityPostResponse response = communityPostService.createPost(null, request, 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Test Title");
        verify(eventPublisher, times(1)).publishEvent(any(PostCreatedEvent.class));
    }

    @Test
    @DisplayName("게시글 조회 시 캐시를 사용한다")
    void getPost_UsesCaching() {
        // Given
        when(postRepository.findByPostIdAndIsDeletedFalse(anyLong())).thenReturn(Optional.of(testPost));

        // When
        CommunityPostResponse response = communityPostService.getPost(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.postId()).isEqualTo(1L);
        verify(postRepository, times(1)).findByPostIdAndIsDeletedFalse(1L);
    }

    @Test
    @DisplayName("게시글 수정 시 캐시가 무효화된다")
    void updatePost_EvictsCache() {
        // Given
        CommunityPostRequest request = new CommunityPostRequest("Updated Title", "Updated Content", "FREE");
        when(postRepository.findByPostIdAndIsDeletedFalse(anyLong())).thenReturn(Optional.of(testPost));

        // When
        CommunityPostResponse response = communityPostService.updatePost(1L, request, 1L);

        // Then
        assertThat(response).isNotNull();
        verify(postRepository, times(1)).findByPostIdAndIsDeletedFalse(1L);
    }
}
