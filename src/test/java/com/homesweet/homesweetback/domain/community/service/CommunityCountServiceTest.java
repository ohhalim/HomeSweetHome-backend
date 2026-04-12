package com.homesweet.homesweetback.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2Provider;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.entity.UserRole;
import com.homesweet.homesweetback.domain.community.dto.PostCounts;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.repository.CommunityCommentLikeRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostLikeRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;

/**
 * CommunityCountService 단위 테스트
 *
 * 테스트 대상:
 * - 조회수 증가/조회 (Redis 캐싱)
 * - 댓글수 증가/감소/조회 (Redis 캐싱)
 * - 게시글 좋아요 토글 (DB 직접)
 * - 댓글 좋아요 토글 (DB 직접)
 * - 벌크 카운터 조회
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityCountService 단위 테스트")
class CommunityCountServiceTest {

    @Mock
    private CommunityPostRepository postRepository;

    @Mock
    private CommunityPostLikeRepository postLikeRepository;

    @Mock
    private CommunityCommentLikeRepository commentLikeRepository;

    @Mock
    private CommunityRedisService redisService;

    @InjectMocks
    private CommunityCountService communityCountService;

    // ===== 헬퍼 =====

    private CommunityPostEntity createPost(Long postId, int viewCount, int commentCount) {
        User author = User.builder()
                .email("author@test.com")
                .name("작성자")
                .provider(OAuth2Provider.GOOGLE)
                .role(UserRole.USER)
                .build();
        author.setId(1L);
        return CommunityPostEntity.builder()
                .postId(postId)
                .author(author)
                .title("테스트")
                .content("내용")
                .category("자유")
                .viewCount(viewCount)
                .commentCount(commentCount)
                .build();
    }

    // ===== 조회수 테스트 =====

    @Nested
    @DisplayName("조회수 테스트")
    class ViewCountTest {

        @Test
        @DisplayName("Redis에 카운터가 있으면 바로 증가한다")
        void increaseViewCount_RedisHit() {
            Long postId = 1L;
            // Redis INCR이 양수를 반환하면 캐시 히트
            given(redisService.incrementPostViewCount(postId)).willReturn(11L);

            communityCountService.increaseViewCount(postId);

            // Redis에 증가 요청만 1번
            verify(redisService).incrementPostViewCount(postId);
            // DB 초기화는 호출되지 않아야 함
            verify(postRepository, never()).findByPostIdAndIsDeletedFalse(postId);
        }

        @Test
        @DisplayName("Redis에 카운터가 없으면 DB에서 초기화 후 증가한다")
        void increaseViewCount_RedisMiss_InitFromDb() {
            Long postId = 1L;
            CommunityPostEntity post = createPost(postId, 10, 5);

            // 첫 호출: Redis에 키 없음(-1), 초기화 후 두 번째 호출: 증가(11)
            given(redisService.incrementPostViewCount(postId))
                    .willReturn(-1L)
                    .willReturn(11L);
            given(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .willReturn(Optional.of(post));

            communityCountService.increaseViewCount(postId);

            verify(redisService).setPostViewCount(postId, 10);
            // increment 2번 호출: 첫 번째(-1), 초기화 후 두 번째(11)
            verify(redisService, org.mockito.Mockito.times(2)).incrementPostViewCount(postId);
        }

        @Test
        @DisplayName("캐시된 조회수를 반환한다")
        void getViewCountFromCache_Hit() {
            Long postId = 1L;
            given(redisService.getPostViewCount(postId)).willReturn(42);

            Integer result = communityCountService.getViewCountFromCache(postId);

            assertThat(result).isEqualTo(42);
            verify(postRepository, never()).findByPostIdAndIsDeletedFalse(postId);
        }

        @Test
        @DisplayName("캐시 미스 시 DB에서 초기화 후 반환한다")
        void getViewCountFromCache_Miss() {
            Long postId = 1L;
            CommunityPostEntity post = createPost(postId, 7, 0);

            // 첫 호출: null(캐시 미스), 초기화 후 두 번째 호출: 7
            given(redisService.getPostViewCount(postId))
                    .willReturn(null)
                    .willReturn(7);
            given(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .willReturn(Optional.of(post));

            Integer result = communityCountService.getViewCountFromCache(postId);

            assertThat(result).isEqualTo(7);
            verify(redisService).setPostViewCount(postId, 7);
        }
    }

    // ===== 댓글수 테스트 =====

    @Nested
    @DisplayName("댓글수 테스트")
    class CommentCountTest {

        @Test
        @DisplayName("Redis에 카운터가 있으면 바로 증가한다")
        void increaseCommentCount_RedisHit() {
            Long postId = 1L;
            given(redisService.incrementPostCommentCount(postId)).willReturn(6L);

            communityCountService.increaseCommentCount(postId);

            verify(redisService).incrementPostCommentCount(postId);
            verify(postRepository, never()).findByPostIdAndIsDeletedFalse(postId);
        }

        @Test
        @DisplayName("Redis에 카운터가 없으면 DB에서 초기화 후 증가한다")
        void increaseCommentCount_RedisMiss() {
            Long postId = 1L;
            CommunityPostEntity post = createPost(postId, 0, 3);

            given(redisService.incrementPostCommentCount(postId))
                    .willReturn(-1L)
                    .willReturn(4L);
            given(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .willReturn(Optional.of(post));

            communityCountService.increaseCommentCount(postId);

            verify(redisService).setPostCommentCount(postId, 3);
        }

        @Test
        @DisplayName("Redis에 카운터가 있으면 바로 감소한다")
        void decreaseCommentCount_RedisHit() {
            Long postId = 1L;
            given(redisService.decreasePostCommentCount(postId)).willReturn(4L);

            communityCountService.decreaseCommentCount(postId);

            verify(redisService).decreasePostCommentCount(postId);
            verify(postRepository, never()).findByPostIdAndIsDeletedFalse(postId);
        }

        @Test
        @DisplayName("Redis에 카운터가 없으면 DB에서 초기화 후 감소한다")
        void decreaseCommentCount_RedisMiss() {
            Long postId = 1L;
            CommunityPostEntity post = createPost(postId, 0, 5);

            given(redisService.decreasePostCommentCount(postId))
                    .willReturn(-1L)
                    .willReturn(4L);
            given(postRepository.findByPostIdAndIsDeletedFalse(postId))
                    .willReturn(Optional.of(post));

            communityCountService.decreaseCommentCount(postId);

            verify(redisService).setPostCommentCount(postId, 5);
        }
    }

    // ===== 게시글 좋아요 토글 테스트 =====

    @Nested
    @DisplayName("게시글 좋아요 토글 테스트")
    class PostLikeToggleTest {

        @Test
        @DisplayName("좋아요가 없으면 추가한다")
        void togglePostLike_Add() {
            Long postId = 1L;
            Long userId = 10L;

            given(postLikeRepository.existsByPost_PostIdAndUser_Id(postId, userId))
                    .willReturn(false);

            communityCountService.togglePostLike(postId, userId);

            verify(postLikeRepository).insertPostLike(postId, userId);
            verify(postLikeRepository, never()).deleteByPostIdAndUserId(postId, userId);
        }

        @Test
        @DisplayName("좋아요가 있으면 삭제한다")
        void togglePostLike_Remove() {
            Long postId = 1L;
            Long userId = 10L;

            given(postLikeRepository.existsByPost_PostIdAndUser_Id(postId, userId))
                    .willReturn(true);

            communityCountService.togglePostLike(postId, userId);

            verify(postLikeRepository).deleteByPostIdAndUserId(postId, userId);
            verify(postLikeRepository, never()).insertPostLike(postId, userId);
        }

        @Test
        @DisplayName("좋아요 여부를 확인한다")
        void isPostLiked() {
            given(postLikeRepository.existsByPost_PostIdAndUser_Id(1L, 10L))
                    .willReturn(true);
            given(postLikeRepository.existsByPost_PostIdAndUser_Id(1L, 20L))
                    .willReturn(false);

            assertThat(communityCountService.isPostLiked(1L, 10L)).isTrue();
            assertThat(communityCountService.isPostLiked(1L, 20L)).isFalse();
        }
    }

    // ===== 댓글 좋아요 토글 테스트 =====

    @Nested
    @DisplayName("댓글 좋아요 토글 테스트")
    class CommentLikeToggleTest {

        @Test
        @DisplayName("댓글 좋아요가 없으면 추가한다")
        void toggleCommentLike_Add() {
            Long commentId = 100L;
            Long userId = 10L;

            given(commentLikeRepository.existsByComment_CommentIdAndUser_Id(commentId, userId))
                    .willReturn(false);

            communityCountService.toggleCommentLike(commentId, userId);

            verify(commentLikeRepository).insertCommentLike(commentId, userId);
            verify(commentLikeRepository, never()).deleteByCommentIdAndUserId(commentId, userId);
        }

        @Test
        @DisplayName("댓글 좋아요가 있으면 삭제한다")
        void toggleCommentLike_Remove() {
            Long commentId = 100L;
            Long userId = 10L;

            given(commentLikeRepository.existsByComment_CommentIdAndUser_Id(commentId, userId))
                    .willReturn(true);

            communityCountService.toggleCommentLike(commentId, userId);

            verify(commentLikeRepository).deleteByCommentIdAndUserId(commentId, userId);
            verify(commentLikeRepository, never()).insertCommentLike(commentId, userId);
        }
    }

    // ===== 카운터 통합 조회 테스트 =====

    @Nested
    @DisplayName("카운터 통합 조회 테스트")
    class GetCountsTest {

        @Test
        @DisplayName("단건 카운터 조회 - 모든 값 반환")
        void getPostCounts_ReturnsCombinedCounts() {
            Long postId = 1L;
            given(redisService.getPostViewCount(postId)).willReturn(100);
            given(postLikeRepository.countByPost_PostId(postId)).willReturn(25L);
            given(redisService.getPostCommentCount(postId)).willReturn(10);

            PostCounts counts = communityCountService.getPostCounts(postId);

            assertThat(counts.viewCount()).isEqualTo(100);
            assertThat(counts.likeCount()).isEqualTo(25);
            assertThat(counts.commentCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("벌크 카운터 조회 - 여러 게시글 한 번에")
        void getBulkPostCounts_ReturnsAll() {
            List<Long> postIds = List.of(1L, 2L);

            // 조회수 벌크
            given(redisService.getBulkViewCounts(postIds))
                    .willReturn(new java.util.HashMap<>(Map.of(1L, 10, 2L, 20)));
            // 댓글수 벌크
            given(redisService.getBulkCommentCounts(postIds))
                    .willReturn(new java.util.HashMap<>(Map.of(1L, 3, 2L, 5)));
            // 좋아요수 (DB)
            given(postLikeRepository.countByPost_PostId(1L)).willReturn(1L);
            given(postLikeRepository.countByPost_PostId(2L)).willReturn(2L);

            Map<Long, PostCounts> result = communityCountService.getBulkPostCounts(postIds);

            assertThat(result).hasSize(2);
            assertThat(result.get(1L).viewCount()).isEqualTo(10);
            assertThat(result.get(1L).likeCount()).isEqualTo(1);
            assertThat(result.get(2L).viewCount()).isEqualTo(20);
            assertThat(result.get(2L).commentCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("벌크 조회수 - 캐시 미스 시 DB에서 초기화")
        void getBulkViewCountsFromCache_MissPartially() {
            List<Long> postIds = List.of(1L, 2L);
            CommunityPostEntity post2 = createPost(2L, 99, 0);

            // 1번은 캐시 히트, 2번은 미스(null)
            given(redisService.getBulkViewCounts(postIds))
                    .willReturn(new java.util.HashMap<>(Map.of(1L, 10)));
            given(postRepository.findByPostIdAndIsDeletedFalse(2L))
                    .willReturn(Optional.of(post2));
            given(redisService.getPostViewCount(2L)).willReturn(99);

            Map<Long, Integer> result = communityCountService.getBulkViewCountsFromCache(postIds);

            assertThat(result.get(1L)).isEqualTo(10);
            assertThat(result.get(2L)).isEqualTo(99);
            verify(redisService).setPostViewCount(2L, 99);
        }

        @Test
        @DisplayName("댓글 좋아요수 DB 조회")
        void getCommentLikeCountFromDb() {
            given(commentLikeRepository.countByComment_CommentId(100L)).willReturn(7L);

            Integer result = communityCountService.getCommentLikeCountFromDb(100L);

            assertThat(result).isEqualTo(7);
        }

        @Test
        @DisplayName("벌크 댓글 좋아요수 조회")
        void getBulkCommentLikeCountsFromDb() {
            List<Long> commentIds = List.of(1L, 2L, 3L);
            given(commentLikeRepository.countByComment_CommentId(1L)).willReturn(3L);
            given(commentLikeRepository.countByComment_CommentId(2L)).willReturn(0L);
            given(commentLikeRepository.countByComment_CommentId(3L)).willReturn(5L);

            Map<Long, Integer> result = communityCountService.getBulkCommentLikeCountsFromDb(commentIds);

            assertThat(result).hasSize(3);
            assertThat(result.get(1L)).isEqualTo(3);
            assertThat(result.get(2L)).isEqualTo(0);
            assertThat(result.get(3L)).isEqualTo(5);
        }
    }
}
