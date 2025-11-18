package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.entity.PostVoteEntity;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import com.homesweet.homesweetback.domain.community.repository.PostVoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * VoteService 단위 테스트
 *
 * @author ohhalim777@gmail.com
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VoteService 단위 테스트")
class VoteServiceTest {

    @Mock
    private CommunityPostRepository postRepository;

    @Mock
    private PostVoteRepository postVoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private VoteService voteService;

    private User testUser;
    private CommunityPostEntity testPost;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .email("test@example.com")
                .build();

        testPost = CommunityPostEntity.builder()
                .postId(1L)
                .author(testUser)
                .title("테스트 게시글")
                .content("테스트 내용")
                .category("일반")
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("새로운 Upvote 추가")
    void testTogglePostVote_NewUpvote() {
        // Given
        when(postRepository.findByPostIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(postVoteRepository.findByPostAndUser(testPost, testUser)).thenReturn(Optional.empty());

        // When
        voteService.togglePostVote(1L, 1L, PostVoteEntity.VoteType.UPVOTE);

        // Then
        verify(postVoteRepository, times(1)).save(any(PostVoteEntity.class));
        assertThat(testPost.getUpvoteCount()).isEqualTo(1);
        assertThat(testPost.getScore()).isEqualTo(1);
    }

    @Test
    @DisplayName("Upvote 취소")
    void testTogglePostVote_CancelUpvote() {
        // Given
        PostVoteEntity existingVote = PostVoteEntity.builder()
                .post(testPost)
                .user(testUser)
                .voteType(PostVoteEntity.VoteType.UPVOTE)
                .build();

        testPost.increaseUpvoteCount(); // 초기 상태: upvote 1개

        when(postRepository.findByPostIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(postVoteRepository.findByPostAndUser(testPost, testUser)).thenReturn(Optional.of(existingVote));

        // When
        voteService.togglePostVote(1L, 1L, PostVoteEntity.VoteType.UPVOTE);

        // Then
        verify(postVoteRepository, times(1)).delete(existingVote);
        assertThat(testPost.getUpvoteCount()).isEqualTo(0);
        assertThat(testPost.getScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("Upvote에서 Downvote로 변경")
    void testTogglePostVote_ChangeUpvoteToDownvote() {
        // Given
        PostVoteEntity existingVote = PostVoteEntity.builder()
                .post(testPost)
                .user(testUser)
                .voteType(PostVoteEntity.VoteType.UPVOTE)
                .build();

        testPost.increaseUpvoteCount(); // 초기 상태: upvote 1개

        when(postRepository.findByPostIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(postVoteRepository.findByPostAndUser(testPost, testUser)).thenReturn(Optional.of(existingVote));

        // When
        voteService.togglePostVote(1L, 1L, PostVoteEntity.VoteType.DOWNVOTE);

        // Then
        verify(postVoteRepository, times(0)).delete(any());
        verify(postVoteRepository, times(0)).save(any());
        assertThat(testPost.getUpvoteCount()).isEqualTo(0);
        assertThat(testPost.getDownvoteCount()).isEqualTo(1);
        assertThat(testPost.getScore()).isEqualTo(-1);
    }

    @Test
    @DisplayName("투표 점수 계산 검증")
    void testScoreCalculation() {
        // Given
        CommunityPostEntity post = CommunityPostEntity.builder()
                .postId(1L)
                .author(testUser)
                .title("테스트")
                .content("내용")
                .category("일반")
                .build();

        // When & Then
        post.increaseUpvoteCount(); // score = 1
        assertThat(post.getScore()).isEqualTo(1);

        post.increaseUpvoteCount(); // score = 2
        assertThat(post.getScore()).isEqualTo(2);

        post.increaseDownvoteCount(); // score = 1
        assertThat(post.getScore()).isEqualTo(1);

        post.increaseDownvoteCount(); // score = 0
        assertThat(post.getScore()).isEqualTo(0);

        post.increaseDownvoteCount(); // score = -1
        assertThat(post.getScore()).isEqualTo(-1);
    }
}
