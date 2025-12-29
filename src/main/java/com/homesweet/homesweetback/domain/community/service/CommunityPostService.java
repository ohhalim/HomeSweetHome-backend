package com.homesweet.homesweetback.domain.community.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.homesweet.homesweetback.common.cache.CacheHelper;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
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
import com.homesweet.homesweetback.domain.search.community.event.CommunityEvent;
import com.homesweet.homesweetback.domain.search.community.event.CommunityEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final CacheHelper cacheHelper;
    private final CommunityConfig config;

    private static final String POST_CACHE_PREFIX = "communityPost::";
    private static final String POST_LIST_CACHE_PREFIX = "communityPostList::";

    /**
     * 게시글 작성
     */
    @Transactional
    public CommunityPostResponse createPost(List<MultipartFile> images, CommunityPostRequest request, Long userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        CommunityPostEntity savedPost = postRepository.save(
                CommunityPostEntity.builder()
                        .author(author)
                        .title(request.title())
                        .content(request.content())
                        .category(request.category())
                        .build());

        List<String> imageUrls = saveImages(images, savedPost);

        communityEventPublisher.publish(CommunityEvent.created(savedPost.getPostId()));
        invalidatePostListCache();

        return CommunityPostResponse.from(savedPost, imageUrls);
    }

    /**
     * 게시글 단건 조회 - Cache-Aside 패턴
     */
    public CommunityPostResponse getPost(Long postId) {
        String cacheKey = POST_CACHE_PREFIX + postId;

        // 1. 캐시 조회
        Optional<CommunityPostResponse> cached = cacheHelper.getFromCache(cacheKey, CommunityPostResponse.class);
        if (cached.isPresent()) {
            PostCounts counts = communityCountService.getPostCounts(postId);
            return withUpdatedCounts(cached.get(), counts);
        }

        // 2. Cache Miss - DB 조회
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        List<String> imageUrls = getImageUrls(post);
        PostCounts counts = communityCountService.getPostCounts(postId);

        CommunityPostResponse response = CommunityPostResponse.fromWithCachedCounts(
                post, imageUrls, counts.viewCount(), counts.likeCount(), counts.commentCount());

        // 3. 캐시 저장
        cacheHelper.setCache(cacheKey, response, config.cache().postTtl());

        return response;
    }

    /**
     * 게시글 수정
     */
    @Transactional
    public CommunityPostResponse updatePost(Long postId, CommunityPostRequest request, Long userId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_POST_FORBIDDEN);
        }

        post.updatePost(request.title(), request.content(), request.category());

        List<String> imageUrls = getImageUrls(post);

        communityEventPublisher.publish(CommunityEvent.updated(post.getPostId()));
        invalidateCaches(postId);

        return CommunityPostResponse.from(post, imageUrls);
    }

    /**
     * 게시글 삭제 (소프트 삭제)
     */
    @Transactional
    public void deletePost(Long postId, Long userId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_POST_FORBIDDEN);
        }

        post.deletePost();
        communityEventPublisher.publish(CommunityEvent.deleted(post.getPostId()));
        invalidateCaches(postId);
    }

    /**
     * 게시글 목록 조회 (페이지네이션) - Cache-Aside 패턴
     */
    public Page<CommunityPostResponse> getPosts(Pageable pageable) {
        String cacheKey = POST_LIST_CACHE_PREFIX + pageable.getPageNumber() + ":" + pageable.getPageSize();

        // 1. 캐시 조회
        Optional<List<CommunityPostResponse>> cached = cacheHelper.getFromCache(
                cacheKey, new TypeReference<List<CommunityPostResponse>>() {
                });

        if (cached.isPresent()) {
            List<Long> postIds = cached.get().stream().map(CommunityPostResponse::postId).toList();
            Map<Long, PostCounts> countsMap = communityCountService.getBulkPostCounts(postIds);

            List<CommunityPostResponse> withLatestCounts = cached.get().stream()
                    .map(post -> withUpdatedCounts(post, countsMap.getOrDefault(post.postId(), PostCounts.zero())))
                    .toList();

            long totalCount = postRepository.countByIsDeletedFalse();
            return new PageImpl<>(withLatestCounts, pageable, totalCount);
        }

        // 2. Cache Miss - DB 조회
        Page<CommunityPostEntity> postsPage = postRepository.findByIsDeletedFalse(pageable);
        List<CommunityPostEntity> posts = postsPage.getContent();

        if (posts.isEmpty()) {
            return postsPage.map(post -> CommunityPostResponse.from(post, null));
        }

        // 이미지 일괄 조회
        Map<Long, List<String>> postImagesMap = getImageUrlsMap(posts);

        // 카운터 일괄 조회
        List<Long> postIds = posts.stream().map(CommunityPostEntity::getPostId).toList();
        Map<Long, PostCounts> countsMap = communityCountService.getBulkPostCounts(postIds);

        List<CommunityPostResponse> responses = posts.stream()
                .map(post -> {
                    List<String> imageUrls = postImagesMap.getOrDefault(post.getPostId(), List.of());
                    PostCounts counts = countsMap.getOrDefault(post.getPostId(), PostCounts.zero());
                    return CommunityPostResponse.fromWithCachedCounts(
                            post, imageUrls, counts.viewCount(), counts.likeCount(), counts.commentCount());
                })
                .toList();

        // 3. 캐시 저장
        cacheHelper.setCache(cacheKey, responses, config.cache().listTtl());

        return new PageImpl<>(responses, pageable, postsPage.getTotalElements());
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================

    private List<String> saveImages(List<MultipartFile> images, CommunityPostEntity post) {
        List<String> imageUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            imageUrls = imageUploader.uploadCommunityImages(images);
            for (int i = 0; i < imageUrls.size(); i++) {
                imageRepository.save(
                        CommunityImageEntity.builder()
                                .post(post)
                                .imageUrl(imageUrls.get(i))
                                .imageOrder(i + 1)
                                .build());
            }
        }
        return imageUrls;
    }

    private List<String> getImageUrls(CommunityPostEntity post) {
        return imageRepository.findByPostOrderByImageOrderAsc(post)
                .stream()
                .map(CommunityImageEntity::getImageUrl)
                .toList();
    }

    private Map<Long, List<String>> getImageUrlsMap(List<CommunityPostEntity> posts) {
        return imageRepository.findAllByPostInOrderByPostPostIdAscImageOrderAsc(posts)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getPost().getPostId(),
                        Collectors.mapping(CommunityImageEntity::getImageUrl, Collectors.toList())));
    }

    private CommunityPostResponse withUpdatedCounts(CommunityPostResponse cached, PostCounts counts) {
        return new CommunityPostResponse(
                cached.postId(), cached.authorId(), cached.authorName(),
                cached.title(), cached.content(), cached.category(),
                counts.viewCount(), counts.likeCount(), counts.commentCount(),
                cached.isModified(), cached.createdAt(), cached.modifiedAt(),
                cached.imagesUrl());
    }

    private void invalidateCaches(Long postId) {
        cacheHelper.deleteCache(POST_CACHE_PREFIX + postId);
        invalidatePostListCache();
        log.debug("Cache invalidated for post: {}", postId);
    }

    private void invalidatePostListCache() {
        int deleted = cacheHelper.invalidateCacheByPattern(POST_LIST_CACHE_PREFIX + "*");
        if (deleted > 0) {
            log.debug("Invalidated {} post list cache entries", deleted);
        }
    }
}
