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
import com.homesweet.homesweetback.domain.subscription.service.SubscriptionService;
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

/**
 * [커뮤니티 게시글 서비스 - 게시글 CRUD(생성/조회/수정/삭제) 담당]
 *
 * [이 서비스가 하는 일]
 * 1. 게시글 작성 (이미지 업로드 포함)
 * 2. 게시글 조회 (단건/목록)
 * 3. 게시글 수정
 * 4. 게시글 삭제 (소프트 삭제 - 실제로 안 지우고 isDeleted 플래그만 true로)
 *
 * [캐싱 전략: Cache-Aside 패턴]
 * 1. 캐시에서 먼저 찾기
 * 2. 캐시에 없으면 DB에서 조회
 * 3. DB에서 가져온 데이터를 캐시에 저장
 * 4. 다음에 같은 데이터 요청 시 캐시에서 빠르게 반환
 */
@Slf4j // 로그 기능 사용
@Service // 비즈니스 로직을 담당하는 서비스 클래스
@Transactional(readOnly = true) // 기본적으로 읽기 전용 트랜잭션 (성능 최적화)
@RequiredArgsConstructor // final 필드 자동 생성자 주입
public class CommunityPostService {

    // 게시글 DB 접근용
    private final CommunityPostRepository postRepository;
    // 게시글 이미지 DB 접근용
    private final CommunityImageRepository imageRepository;
    // 사용자 정보 조회용
    private final UserRepository userRepository;
    // 이미지 S3 업로드 담당
    private final CommunityImageUploader imageUploader;
    // 조회수, 좋아요수, 댓글수 관리 (Redis 기반)
    private final CommunityCountService communityCountService;
    // Redis 캐시 읽기/쓰기 헬퍼
    private final CacheHelper cacheHelper;
    // 캐시 TTL(유효시간) 등 설정값
    private final CommunityConfig config;
    // 구독 확인용 (프리미엄 커뮤니티)
    private final SubscriptionService subscriptionService;

    // 캐시 키 접두어 (이걸로 게시글 캐시인지 구분)
    private static final String POST_CACHE_PREFIX = "communityPost::";
    // 게시글 목록 캐시 키 접두어
    private static final String POST_LIST_CACHE_PREFIX = "communityPostList::";

    /**
     * [게시글 작성]
     *
     * [하는 일]
     * 1. 작성자 정보 조회
     * 2. 게시글을 DB에 저장
     * 3. 이미지가 있으면 S3에 업로드하고 URL을 DB에 저장
     * 4. 게시글 목록 캐시 삭제 (새 글이 추가됐으니까)
     *
     * @param images  업로드할 이미지 파일들 (없을 수도 있음)
     * @param request 게시글 내용 (제목, 내용, 카테고리)
     * @param userId  작성자 ID
     * @return 생성된 게시글 정보
     */
    @Transactional // 쓰기 작업이라 readOnly 해제
    public CommunityPostResponse createPost(List<MultipartFile> images, CommunityPostRequest request, Long userId) {
        // 0. 구독 확인 (비구독자 차단)
        subscriptionService.validateSubscription(userId);

        // 1. 작성자 정보 조회 (없으면 예외 발생)
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        // 2. 게시글 엔티티 생성 후 DB에 저장
        CommunityPostEntity savedPost = postRepository.save(
                CommunityPostEntity.builder()
                        .author(author)
                        .title(request.title())
                        .content(request.content())
                        .category(request.category())
                        .build());

        // 3. 이미지가 있으면 S3에 업로드하고 DB에 저장
        List<String> imageUrls = saveImages(images, savedPost);

        // 4. 새 글이 추가됐으니 목록 캐시 삭제 (다음 조회 시 DB에서 새로 가져옴)
        invalidatePostListCache();

        return CommunityPostResponse.from(savedPost, imageUrls);
    }

    /**
     * [게시글 단건 조회 - Cache-Aside 패턴]
     *
     * [동작 흐름]
     * 1. 캐시에서 먼저 찾기
     * 2. 캐시에 있으면 -> 최신 카운터(조회수, 좋아요수, 댓글수)와 합쳐서 반환
     * 3. 캐시에 없으면 -> DB에서 조회 -> 캐시에 저장 -> 반환
     *
     * [왜 카운터는 별도로 조회해?]
     * 조회수, 좋아요수는 자주 바뀌니까 캐시에 저장하면 금방 오래된 값이 돼.
     * 그래서 Redis에서 실시간 카운터를 따로 관리하고, 조회 시 합쳐주는 거야.
     */
    public CommunityPostResponse getPost(Long postId) {
        String cacheKey = POST_CACHE_PREFIX + postId;

        // 1. 캐시에서 조회
        Optional<CommunityPostResponse> cached = cacheHelper.getFromCache(cacheKey, CommunityPostResponse.class);
        if (cached.isPresent()) {
            // 캐시에 있으면 최신 카운터 가져와서 합치기
            PostCounts counts = communityCountService.getPostCounts(postId);
            return withUpdatedCounts(cached.get(), counts);
        }

        // 2. 캐시에 없으면 DB에서 조회
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        // 이미지 URL 목록 가져오기
        List<String> imageUrls = getImageUrls(post);
        // 최신 카운터 가져오기
        PostCounts counts = communityCountService.getPostCounts(postId);

        // 응답 객체 생성 (카운터 포함)
        CommunityPostResponse response = CommunityPostResponse.fromWithCachedCounts(
                post, imageUrls, counts.viewCount(), counts.likeCount(), counts.commentCount());

        // 3. 캐시에 저장 (다음 조회 시 빠르게 반환하려고)
        cacheHelper.setCache(cacheKey, response, config.cache().postTtl());

        return response;
    }

    /**
     * [게시글 수정]
     *
     * [하는 일]
     * 1. 게시글이 존재하는지 확인
     * 2. 본인이 작성한 글인지 확인 (아니면 403 Forbidden)
     * 3. 내용 수정
     * 4. 관련 캐시 삭제 (오래된 데이터 방지)
     */
    @Transactional
    public CommunityPostResponse updatePost(Long postId, CommunityPostRequest request, Long userId) {
        // 1. 게시글 조회
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        // 2. 본인 글인지 확인
        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_POST_FORBIDDEN);
        }

        // 3. 내용 수정 (엔티티 메서드 호출)
        post.updatePost(request.title(), request.content(), request.category());

        List<String> imageUrls = getImageUrls(post);

        // 4. 캐시 삭제 (수정됐으니 다음에 DB에서 새로 가져와야 함)
        invalidateCaches(postId);

        return CommunityPostResponse.from(post, imageUrls);
    }

    /**
     * [게시글 삭제 - 소프트 삭제]
     *
     * [소프트 삭제란?]
     * 실제로 DB에서 지우지 않고 isDeleted 플래그만 true로 바꿈.
     * 왜? 나중에 복구하거나 통계 낼 때 필요할 수 있어서.ㄷ
     *
     * [하는 일]
     * 1. 게시글 존재 확인
     * 2. 본인 글인지 확인
     * 3. isDeleted = true 로 변경
     * 4. 캐시 삭제
     */
    @Transactional
    public void deletePost(Long postId, Long userId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_POST_FORBIDDEN);
        }

        // 소프트 삭제 (isDeleted = true)
        post.deletePost();
        invalidateCaches(postId);
    }

    /**
     * [게시글 목록 조회 - 페이지네이션 + 캐싱]
     *
     * [동작 흐름]
         * 1. 캐시에서 해당 페이지 데이터 찾기
     * 2. 있으면 -> 최신 카운터와 합쳐서 반환
     * 3. 없으면 -> DB에서 조회 -> 캐시에 저장 -> 반환
     *
     * [페이지네이션이란?]
     * 게시글이 1000개면 한 번에 다 보여주면 느리니까,
     * 10개씩 나눠서 페이지별로 보여주는 거야.
     * 예: 1페이지(1~10번), 2페이지(11~20번)...
     */
    public Page<CommunityPostResponse> getPosts(Pageable pageable) {
        // 캐시 키: "communityPostList::0:10" (0페이지, 10개)
        String cacheKey = POST_LIST_CACHE_PREFIX + pageable.getPageNumber() + ":" + pageable.getPageSize();

        // 1. 캐시에서 조회
        Optional<List<CommunityPostResponse>> cached = cacheHelper.getFromCache(
                cacheKey, new TypeReference<List<CommunityPostResponse>>() {
                });

        if (cached.isPresent()) {
            // 캐시 히트! 최신 카운터와 합치기
            List<Long> postIds = cached.get().stream().map(CommunityPostResponse::postId).toList();
            // 여러 게시글의 카운터를 한 번에 조회 (N+1 문제 방지)
            Map<Long, PostCounts> countsMap = communityCountService.getBulkPostCounts(postIds);

            // 각 게시글에 최신 카운터 적용
            List<CommunityPostResponse> withLatestCounts = cached.get().stream()
                    .map(post -> withUpdatedCounts(post, countsMap.getOrDefault(post.postId(), PostCounts.zero())))
                    .toList();

            // 전체 게시글 수 (페이지 정보에 필요)
            long totalCount = postRepository.countByIsDeletedFalse();
            return new PageImpl<>(withLatestCounts, pageable, totalCount);
        }

        // 2. 캐시 미스 -> DB에서 조회
        Page<CommunityPostEntity> postsPage = postRepository.findByIsDeletedFalse(pageable);
        List<CommunityPostEntity> posts = postsPage.getContent();

        // 게시글이 없으면 빈 페이지 반환
        if (posts.isEmpty()) {
            return postsPage.map(post -> CommunityPostResponse.from(post, null));
        }

        // 이미지 일괄 조회 (N+1 문제 방지)
        // N+1 문제: 게시글 10개면 이미지 쿼리도 10번 -> 비효율
        // 해결: 한 번에 10개 게시글의 이미지를 모두 조회
        Map<Long, List<String>> postImagesMap = getImageUrlsMap(posts);

        // 카운터 일괄 조회 (Redis MGET 사용)
        List<Long> postIds = posts.stream().map(CommunityPostEntity::getPostId).toList();
        Map<Long, PostCounts> countsMap = communityCountService.getBulkPostCounts(postIds);

        // 응답 객체 리스트 생성
        List<CommunityPostResponse> responses = posts.stream()
                .map(post -> {
                    List<String> imageUrls = postImagesMap.getOrDefault(post.getPostId(), List.of());
                    PostCounts counts = countsMap.getOrDefault(post.getPostId(), PostCounts.zero());
                    return CommunityPostResponse.fromWithCachedCounts(
                            post, imageUrls, counts.viewCount(), counts.likeCount(), counts.commentCount());
                })
                .toList();

        // 3. 캐시에 저장
        cacheHelper.setCache(cacheKey, responses, config.cache().listTtl());

        return new PageImpl<>(responses, pageable, postsPage.getTotalElements());
    }

    // ============================================================
    // [내부 헬퍼 메서드들 - 중복 코드를 줄이기 위한 공통 로직]
    // ============================================================

    /**
     * [이미지 S3 업로드 후 DB에 저장]
     * 게시글에 첨부된 이미지들을 S3에 업로드하고, URL을 DB에 저장해.
     */
    private List<String> saveImages(List<MultipartFile> images, CommunityPostEntity post) {
        List<String> imageUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            // S3에 이미지 업로드하고 URL 받기
            imageUrls = imageUploader.uploadCommunityImages(images);
            // 각 이미지 URL을 DB에 저장 (순서 정보 포함)
            for (int i = 0; i < imageUrls.size(); i++) {
                imageRepository.save(
                        CommunityImageEntity.builder()
                                .post(post)
                                .imageUrl(imageUrls.get(i))
                                .imageOrder(i + 1) // 1부터 시작하는 순서
                                .build());
            }
        }
        return imageUrls;
    }

    /**
     * [게시글의 이미지 URL 목록 조회]
     */
    private List<String> getImageUrls(CommunityPostEntity post) {
        return imageRepository.findByPostOrderByImageOrderAsc(post)
                .stream()
                .map(CommunityImageEntity::getImageUrl)
                .toList();
    }

    /**
     * [여러 게시글의 이미지 URL을 한 번에 조회]
     * N+1 문제 방지를 위해 한 번의 쿼리로 여러 게시글의 이미지를 모두 가져옴.
     *
     * @return Map<게시글ID, 이미지URL 리스트>
     */
    private Map<Long, List<String>> getImageUrlsMap(List<CommunityPostEntity> posts) {
        return imageRepository.findAllByPostInOrderByPostPostIdAscImageOrderAsc(posts)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getPost().getPostId(), // 게시글 ID로 그룹핑
                        Collectors.mapping(CommunityImageEntity::getImageUrl, Collectors.toList())));
    }

    /**
     * [캐시된 응답에 최신 카운터 적용]
     * 캐시에서 가져온 게시글에 Redis의 최신 조회수/좋아요수/댓글수를 합침.
     */
    private CommunityPostResponse withUpdatedCounts(CommunityPostResponse cached, PostCounts counts) {
        return new CommunityPostResponse(
                cached.postId(), cached.authorId(), cached.authorName(),
                cached.title(), cached.content(), cached.category(),
                counts.viewCount(), counts.likeCount(), counts.commentCount(),
                cached.isModified(), cached.createdAt(), cached.modifiedAt(),
                cached.imagesUrl());
    }

    /**
     * [게시글 관련 캐시 삭제]
     * 게시글이 수정/삭제되면 캐시도 지워야 함.
     * 안 그러면 사용자가 오래된 데이터를 보게 돼!
     */
    private void invalidateCaches(Long postId) {
        // 단건 캐시 삭제
        cacheHelper.deleteCache(POST_CACHE_PREFIX + postId);
        // 목록 캐시도 삭제 (순서가 바뀔 수 있으니까)
        invalidatePostListCache();
        log.debug("Cache invalidated for post: {}", postId);
    }

    /**
     * [게시글 목록 캐시 전체 삭제]
     * 새 글이 작성되거나 삭제되면 목록이 바뀌니까 전체 캐시 삭제.
     */
    private void invalidatePostListCache() {
        // 패턴 매칭으로 "communityPostList::*" 키 모두 삭제
        int deleted = cacheHelper.invalidateCacheByPattern(POST_LIST_CACHE_PREFIX + "*");
        if (deleted > 0) {
            log.debug("Invalidated {} post list cache entries", deleted);
        }
    }
}
