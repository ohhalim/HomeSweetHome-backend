package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostResponse;
import com.homesweet.homesweetback.domain.community.dto.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.entity.CommunityImageEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.event.PostCreatedEvent;
import com.homesweet.homesweetback.domain.community.repository.CommunityImageRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Community 서비스
 *
 * @author ohhalim777@gmail.com
 * @date 25. 10. 21.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostService {

    private final CommunityPostRepository postRepository;
    private final CommunityImageRepository imageRepository;
    private final UserRepository userRepository;
    private final CommunityImageUploader imageUploader;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 게시글 작성
     */
    @Transactional
    @CacheEvict(value = "communityPostListCache", allEntries = true)
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

        // 게시글 생성 이벤트 발행
        eventPublisher.publishEvent(new PostCreatedEvent(
            this,
            savedPost.getPostId(),
            userId,
            savedPost.getTitle(),
            savedPost.getCategory()
        ));

        return CommunityPostResponse.from(savedPost, imageUrls);
    }

    /**
     * 게시글 단건 조회
     */
    @Cacheable(value = "communityPostCache", key = "#postId", unless = "#result == null")
    public CommunityPostResponse getPost(Long postId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        // 이미지 조회
        List<String> imageUrls = imageRepository.findByPostOrderByImageOrderAsc(post)
                .stream()
                .map(CommunityImageEntity::getImageUrl)
                .toList();

        return CommunityPostResponse.from(post, imageUrls);
    }

    /**
     * 게시글 수정
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "communityPostCache", key = "#postId"),
        @CacheEvict(value = "communityPostListCache", allEntries = true)
    })
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

        return CommunityPostResponse.from(post, imageUrls);
    }

    /**
     * 게시글 삭제 (소프트 삭제)
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "communityPostCache", key = "#postId"),
        @CacheEvict(value = "communityPostListCache", allEntries = true)
    })
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
    }

    /**
     * 게시글 목록 조회 (페이지네이션)
     */
    @Cacheable(
        value = "communityPostListCache",
        key = "'page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize",
        unless = "#result == null || #result.isEmpty()"
    )
    public Page<CommunityPostResponse> getPosts(Pageable pageable) {
        Page<CommunityPostEntity> posts = postRepository.findByIsDeletedFalse(pageable);
        return posts.map(post -> {
            List<String> imageUrls = imageRepository.findByPostOrderByImageOrderAsc(post)
                    .stream()
                    .map(CommunityImageEntity::getImageUrl)
                    .toList();
            return CommunityPostResponse.from(post, imageUrls);
        });
    }
}
