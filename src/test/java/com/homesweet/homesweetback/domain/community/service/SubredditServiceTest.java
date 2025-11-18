package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.entity.SubredditEntity;
import com.homesweet.homesweetback.domain.community.entity.SubredditModeratorEntity;
import com.homesweet.homesweetback.domain.community.repository.SubredditModeratorRepository;
import com.homesweet.homesweetback.domain.community.repository.SubredditRepository;
import com.homesweet.homesweetback.domain.community.repository.SubredditSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SubredditService 단위 테스트
 *
 * @author ohhalim777@gmail.com
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubredditService 단위 테스트")
class SubredditServiceTest {

    @Mock
    private SubredditRepository subredditRepository;

    @Mock
    private SubredditSubscriptionRepository subscriptionRepository;

    @Mock
    private SubredditModeratorRepository moderatorRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubredditService subredditService;

    private User testUser;
    private SubredditEntity testSubreddit;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .email("test@example.com")
                .build();

        testSubreddit = SubredditEntity.builder()
                .subredditId(1L)
                .name("programming")
                .title("프로그래밍")
                .description("프로그래밍 토론 커뮤니티")
                .creator(testUser)
                .build();
    }

    @Test
    @DisplayName("서브레딧 생성 성공")
    void testCreateSubreddit_Success() {
        // Given
        String name = "programming";
        String title = "프로그래밍";
        String description = "프로그래밍 토론";

        when(subredditRepository.existsByName(name)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(subredditRepository.save(any(SubredditEntity.class))).thenReturn(testSubreddit);
        when(subscriptionRepository.existsBySubredditAndUser(any(), any())).thenReturn(false);

        // When
        SubredditEntity created = subredditService.createSubreddit(name, title, description, false, false, 1L);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo(name);
        verify(subredditRepository, times(1)).save(any(SubredditEntity.class));
        verify(moderatorRepository, times(1)).save(any(SubredditModeratorEntity.class));
    }

    @Test
    @DisplayName("서브레딧 생성 실패 - 중복 이름")
    void testCreateSubreddit_DuplicateName() {
        // Given
        String name = "programming";
        when(subredditRepository.existsByName(name)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() ->
                subredditService.createSubreddit(name, "제목", "설명", false, false, 1L))
                .isInstanceOf(CommunityException.class);
    }

    @Test
    @DisplayName("서브레딧 생성 실패 - 잘못된 이름 형식")
    void testCreateSubreddit_InvalidName() {
        // Given
        String invalidName = "프로그래밍"; // 한글 사용

        // When & Then
        assertThatThrownBy(() ->
                subredditService.createSubreddit(invalidName, "제목", "설명", false, false, 1L))
                .isInstanceOf(CommunityException.class);
    }

    @Test
    @DisplayName("서브레딧 구독 성공")
    void testSubscribeToSubreddit_Success() {
        // Given
        when(subredditRepository.findBySubredditIdAndIsActiveTrueAndIsDeletedFalse(1L))
                .thenReturn(Optional.of(testSubreddit));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(subscriptionRepository.existsBySubredditAndUser(testSubreddit, testUser)).thenReturn(false);

        int initialCount = testSubreddit.getSubscriberCount();

        // When
        subredditService.subscribeToSubreddit(1L, 1L);

        // Then
        verify(subscriptionRepository, times(1)).save(any());
        assertThat(testSubreddit.getSubscriberCount()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("모더레이터 권한 확인")
    void testHasModeratorPermission() {
        // Given
        SubredditModeratorEntity moderator = SubredditModeratorEntity.builder()
                .subreddit(testSubreddit)
                .user(testUser)
                .permission(SubredditModeratorEntity.ModeratorPermission.FULL)
                .build();

        when(subredditRepository.findBySubredditIdAndIsActiveTrueAndIsDeletedFalse(1L))
                .thenReturn(Optional.of(testSubreddit));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(moderatorRepository.findBySubredditAndUser(testSubreddit, testUser))
                .thenReturn(Optional.of(moderator));

        // When
        boolean hasFull = subredditService.hasModeratorPermission(1L, 1L,
                SubredditModeratorEntity.ModeratorPermission.FULL);
        boolean hasPosts = subredditService.hasModeratorPermission(1L, 1L,
                SubredditModeratorEntity.ModeratorPermission.POSTS);

        // Then
        assertThat(hasFull).isTrue(); // FULL 권한은 FULL 체크 통과
        assertThat(hasPosts).isTrue(); // FULL 권한은 POSTS 체크도 통과
    }
}
