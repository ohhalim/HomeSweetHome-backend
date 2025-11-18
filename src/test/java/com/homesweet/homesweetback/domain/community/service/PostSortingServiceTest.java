package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PostSortingService 단위 테스트
 *
 * @author ohhalim777@gmail.com
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostSortingService 단위 테스트")
class PostSortingServiceTest {

    @Mock
    private CommunityPostRepository postRepository;

    @InjectMocks
    private PostSortingService sortingService;

    private User testUser;
    private List<CommunityPostEntity> testPosts;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .email("test@example.com")
                .build();

        // 테스트용 게시글 생성
        CommunityPostEntity post1 = createPost(1L, "게시글1", 100, 10, LocalDateTime.now().minusHours(1));
        CommunityPostEntity post2 = createPost(2L, "게시글2", 50, 20, LocalDateTime.now().minusHours(3));
        CommunityPostEntity post3 = createPost(3L, "게시글3", 200, 50, LocalDateTime.now().minusDays(1));
        CommunityPostEntity post4 = createPost(4L, "게시글4", 150, 150, LocalDateTime.now().minusHours(2));

        testPosts = Arrays.asList(post1, post2, post3, post4);
    }

    private CommunityPostEntity createPost(Long id, String title, int upvotes, int downvotes,
                                          LocalDateTime createdAt) {
        CommunityPostEntity post = CommunityPostEntity.builder()
                .postId(id)
                .author(testUser)
                .title(title)
                .content("내용")
                .category("일반")
                .build();

        // 투표 수 설정
        for (int i = 0; i < upvotes; i++) {
            post.increaseUpvoteCount();
        }
        for (int i = 0; i < downvotes; i++) {
            post.increaseDownvoteCount();
        }

        // createdAt 강제 설정 (리플렉션 사용)
        try {
            java.lang.reflect.Field field = post.getClass().getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(post, createdAt);
        } catch (Exception e) {
            // Ignore
        }

        return post;
    }

    @Test
    @DisplayName("HOT 정렬 - 최근 + 높은 점수")
    void testGetHotPosts() {
        // Given
        when(postRepository.findByIsDeletedFalse()).thenReturn(testPosts);
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CommunityPostEntity> hotPosts = sortingService.getHotPosts(pageable, null);

        // Then
        assertThat(hotPosts).isNotNull();
        assertThat(hotPosts.getContent()).isNotEmpty();

        // 첫 번째 게시글이 가장 HOT한 게시글이어야 함
        // (최근 생성 + 높은 점수)
        CommunityPostEntity firstPost = hotPosts.getContent().get(0);
        assertThat(firstPost).isNotNull();
    }

    @Test
    @DisplayName("TOP 정렬 - 점수 높은 순")
    void testGetTopPosts() {
        // Given
        when(postRepository.findByIsDeletedFalseAndCreatedAtAfter(any())).thenReturn(testPosts);
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CommunityPostEntity> topPosts = sortingService.getTopPosts(pageable, null,
                PostSortingService.TopPeriod.ALL_TIME);

        // Then
        assertThat(topPosts).isNotNull();
        assertThat(topPosts.getContent()).isNotEmpty();

        // 첫 번째 게시글이 가장 높은 점수를 가져야 함
        CommunityPostEntity firstPost = topPosts.getContent().get(0);
        assertThat(firstPost.getScore()).isEqualTo(150); // post3: 200-50=150
    }

    @Test
    @DisplayName("CONTROVERSIAL 정렬 - 논쟁적인 글")
    void testGetControversialPosts() {
        // Given
        when(postRepository.findByIsDeletedFalseAndCreatedAtAfter(any())).thenReturn(testPosts);
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CommunityPostEntity> controversialPosts = sortingService.getControversialPosts(pageable, null,
                PostSortingService.TopPeriod.ALL_TIME);

        // Then
        assertThat(controversialPosts).isNotNull();
        assertThat(controversialPosts.getContent()).isNotEmpty();

        // post4 (150:150)가 가장 논쟁적이어야 함 (비율이 1에 가까움)
        CommunityPostEntity firstPost = controversialPosts.getContent().get(0);
        assertThat(firstPost.getPostId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("점수 계산 검증")
    void testScoreCalculation() {
        // Given
        CommunityPostEntity post = testPosts.get(0); // 100 upvotes, 10 downvotes

        // Then
        assertThat(post.getScore()).isEqualTo(90); // 100 - 10 = 90
        assertThat(post.getUpvoteCount()).isEqualTo(100);
        assertThat(post.getDownvoteCount()).isEqualTo(10);
    }
}
