package com.homesweet.homesweetback.domain.community.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostResponse;
import com.homesweet.homesweetback.domain.community.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.entity.CommunityImageEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.search.community.event.CommunityEvent;
import com.homesweet.homesweetback.domain.search.community.event.CommunityEventPublisher;
import com.homesweet.homesweetback.domain.community.repository.CommunityImageRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommunityPostService {

    private final CommunityEventPublisher communityEventPublisher;
    private final CommunityPostRepository postRepository;
    private final CommunityImageRepository imageRepository;
    private final UserRepository userRepository;
    private final CommunityImageUploader imageUploader;
    private final CommunityCountService communityCountService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String POST_CACHE_PREFIX = "communityPost::";
    private static final String POST_LIST_CACHE_PREFIX = "communityPostList::";
    private static final Duration POST_CACHE_TTL = Duration.ofHours(1);
    private static final Duration POST_LIST_CACHE_TTL = Duration.ofMinutes(1); // 목록은 짧게

    /**
     * 게시글 목록 캐시 무효화 (SCAN 사용 - 프로덕션 안전, 논블로킹)
     */
    private void invalidatePostListCache() {
        try {
            var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(POST_LIST_CACHE_PREFIX + "*")
                    .count(100)
                    .build();
            
            int deletedCount = 0;
            try (var cursor = stringRedisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    stringRedisTemplate.delete(key);
                    deletedCount++;
                }
            }
            
            if (deletedCount > 0) {
                log.debug("Invalidated {} post list cache entries", deletedCount);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate post list cache", e);
        }
    }

    /**
     * 게시글 작성
     */
    @Transactional
    public CommunityPostResponse createPost(List<MultipartFile> images, CommunityPostRequest request, Long userId) {
        // User 조회
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        CommunityPostEntity savedPost = postRepository.save(
                CommunityPostEntity.builder()
                        .author(author)
                        .title(request.title())
                        .content(request.content())
                        .category(request.category())
                        .build()
        );

        // 이미지 업로드 및 저장
        List<String> imageUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            imageUrls = imageUploader.uploadCommunityImages(images);

            for (int i = 0; i < imageUrls.size(); i++) {
                imageRepository.save(
                        CommunityImageEntity.builder()
                                .post(savedPost)
                                .imageUrl(imageUrls.get(i))
                                .imageOrder(i + 1) // 0이 아닌 1부터 시작하도록 수정
                                .build()
                );
            }
        }

        communityEventPublisher.publish(CommunityEvent.created(savedPost.getPostId()));

        // 목록 캐시 무효화
        invalidatePostListCache();

        return CommunityPostResponse.from(savedPost, imageUrls);
    }

    /**
     * 게시글 단건 조회 - Cache-Aside 패턴 적용 (게시물 본문 + 카운터)
     */
    public CommunityPostResponse getPost(Long postId) {
        String cacheKey = POST_CACHE_PREFIX + postId;

        // 1. 캐시 조회
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                CommunityPostResponse cached = objectMapper.readValue(cachedJson, CommunityPostResponse.class);
                // 카운터는 항상 Redis에서 최신값 조회
                Integer viewCount = communityCountService.getViewCountFromCache(postId);
                Integer likeCount = communityCountService.getLikeCountFromCache(postId);
                Integer commentCount = communityCountService.getCommentCountFromCache(postId);
                
                return new CommunityPostResponse(
                        cached.postId(), cached.authorId(), cached.authorName(),
                        cached.title(), cached.content(), cached.category(),
                        viewCount, likeCount, commentCount,
                        cached.isModified(), cached.createdAt(), cached.modifiedAt(),
                        cached.imagesUrl()
                );
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached post, fetching from DB: {}", postId, e);
                stringRedisTemplate.delete(cacheKey);
            }
        }

        // 2. Cache Miss - DB 조회
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        List<String> imageUrls = imageRepository.findByPostOrderByImageOrderAsc(post)
                .stream()
                .map(CommunityImageEntity::getImageUrl)
                .toList();

        // Redis에서 실시간 카운터 조회
        Integer viewCount = communityCountService.getViewCountFromCache(postId);
        Integer likeCount = communityCountService.getLikeCountFromCache(postId);
        Integer commentCount = communityCountService.getCommentCountFromCache(postId);

        CommunityPostResponse response = CommunityPostResponse.fromWithCachedCounts(
                post, imageUrls, viewCount, likeCount, commentCount);

        // 3. 캐시 저장
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(cacheKey, json, POST_CACHE_TTL);
            log.debug("Cached post: {}", postId);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache post: {}", postId, e);
        }

        return response;
    }

    /**
     * 게시글 수정
     */
    @Transactional
    public CommunityPostResponse updatePost(Long postId, CommunityPostRequest request, Long userId) {
        // 게시글 조회
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        // 작성자 본인 확인
        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_POST_FORBIDDEN);
        }

        // 게시글 수정
        post.updatePost(request.title(), request.content(), request.category());

        // 이미지 조회
        List<String> imageUrls = imageRepository.findByPostOrderByImageOrderAsc(post)
                .stream()
                .map(CommunityImageEntity::getImageUrl)
                .toList();

        communityEventPublisher.publish(CommunityEvent.updated(post.getPostId()));

        // 캐시 무효화
        stringRedisTemplate.delete(POST_CACHE_PREFIX + postId);
        invalidatePostListCache();
        log.debug("Cache invalidated for updated post: {}", postId);

        return CommunityPostResponse.from(post, imageUrls);
    }

    /**
     * 게시글 삭제 (소프트 삭제)
     */
    @Transactional
    public void deletePost(Long postId, Long userId) {
        // 게시글 조회
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        // 작성자 본인 확인
        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_POST_FORBIDDEN);
        }

        // 게시글 소프트 삭제
        post.deletePost();
        communityEventPublisher.publish(CommunityEvent.deleted(post.getPostId()));

        // 캐시 무효화
        stringRedisTemplate.delete(POST_CACHE_PREFIX + postId);
        invalidatePostListCache();
        log.debug("Cache invalidated for deleted post: {}", postId);
    }

    /**
     * 게시글 목록 조회 (페이지네이션) - Cache-Aside 패턴 적용
     */
    public Page<CommunityPostResponse> getPosts(Pageable pageable) {
        String cacheKey = POST_LIST_CACHE_PREFIX + pageable.getPageNumber() + ":" + pageable.getPageSize();

        // 1. 캐시 조회
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                List<CommunityPostResponse> cached = objectMapper.readValue(
                        cachedJson, new TypeReference<List<CommunityPostResponse>>() {});
                
                // 카운터는 항상 최신값으로 조회 (Bulk MGET - 30번 → 3번 최적화)
                List<Long> postIds = cached.stream().map(CommunityPostResponse::postId).toList();
                Map<Long, Integer> viewCounts = communityCountService.getBulkViewCountsFromCache(postIds);
                Map<Long, Integer> likeCounts = communityCountService.getBulkLikeCountsFromCache(postIds);
                Map<Long, Integer> commentCounts = communityCountService.getBulkCommentCountsFromCache(postIds);

                List<CommunityPostResponse> withLatestCounts = cached.stream()
                        .map(post -> new CommunityPostResponse(
                                post.postId(), post.authorId(), post.authorName(),
                                post.title(), post.content(), post.category(),
                                viewCounts.getOrDefault(post.postId(), 0),
                                likeCounts.getOrDefault(post.postId(), 0),
                                commentCounts.getOrDefault(post.postId(), 0),
                                post.isModified(), post.createdAt(), post.modifiedAt(),
                                post.imagesUrl()
                        ))
                        .toList();
                
                long totalCount = postRepository.countByIsDeletedFalse();
                log.debug("Cache hit for post list: page={}", pageable.getPageNumber());
                return new org.springframework.data.domain.PageImpl<>(withLatestCounts, pageable, totalCount);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached post list", e);
                stringRedisTemplate.delete(cacheKey);
            }
        }

        // 2. Cache Miss - DB 조회
        Page<CommunityPostEntity> postsPage = postRepository.findByIsDeletedFalse(pageable);
        List<CommunityPostEntity> posts = postsPage.getContent();

        if (posts.isEmpty()) {
            return postsPage.map(post -> CommunityPostResponse.from(post, null));
        }
        
        List<CommunityImageEntity> allImages = imageRepository.findAllByPostInOrderByPostPostIdAscImageOrderAsc(posts);

        Map<Long, List<String>> postImagesMap = allImages.stream()
                .collect(Collectors.groupingBy(
                        image -> image.getPost().getPostId(),
                        Collectors.mapping(CommunityImageEntity::getImageUrl, Collectors.toList())
                ));

        // Bulk 카운터 조회 (MGET - 30번 → 3번 최적화)
        List<Long> postIds = posts.stream().map(CommunityPostEntity::getPostId).toList();
        Map<Long, Integer> viewCounts = communityCountService.getBulkViewCountsFromCache(postIds);
        Map<Long, Integer> likeCounts = communityCountService.getBulkLikeCountsFromCache(postIds);
        Map<Long, Integer> commentCounts = communityCountService.getBulkCommentCountsFromCache(postIds);

        List<CommunityPostResponse> responses = posts.stream()
                .map(post -> {
                    List<String> imageUrls = postImagesMap.getOrDefault(post.getPostId(), List.of());
                    return CommunityPostResponse.fromWithCachedCounts(
                            post, imageUrls,
                            viewCounts.getOrDefault(post.getPostId(), 0),
                            likeCounts.getOrDefault(post.getPostId(), 0),
                            commentCounts.getOrDefault(post.getPostId(), 0)
                    );
                })
                .toList();

        // 3. 캐시 저장
        try {
            String json = objectMapper.writeValueAsString(responses);
            stringRedisTemplate.opsForValue().set(cacheKey, json, POST_LIST_CACHE_TTL);
            log.debug("Cached post list: page={}", pageable.getPageNumber());
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache post list", e);
        }

        return new org.springframework.data.domain.PageImpl<>(responses, pageable, postsPage.getTotalElements());
    }
}
