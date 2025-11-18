package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.common.lock.DistributedLock;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.entity.SubredditEntity;
import com.homesweet.homesweetback.domain.community.entity.SubredditModeratorEntity;
import com.homesweet.homesweetback.domain.community.entity.SubredditSubscriptionEntity;
import com.homesweet.homesweetback.domain.community.repository.SubredditModeratorRepository;
import com.homesweet.homesweetback.domain.community.repository.SubredditRepository;
import com.homesweet.homesweetback.domain.community.repository.SubredditSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Subreddit 서비스
 *
 * Reddit-style 서브레딧 관리
 * - 서브레딧 생성/수정/삭제
 * - 구독/구독 취소
 * - 모더레이터 관리
 *
 * @author ohhalim777@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubredditService {

    private final SubredditRepository subredditRepository;
    private final SubredditSubscriptionRepository subscriptionRepository;
    private final SubredditModeratorRepository moderatorRepository;
    private final UserRepository userRepository;

    /**
     * 서브레딧 생성
     *
     * 생성자는 자동으로 FULL 권한 모더레이터가 됨
     */
    @Transactional
    @CacheEvict(value = "subredditListCache", allEntries = true)
    public SubredditEntity createSubreddit(String name, String title, String description,
                                          Boolean isPrivate, Boolean isNsfw, Long userId) {
        // 이름 중복 체크
        if (subredditRepository.existsByName(name)) {
            throw new CommunityException(ErrorCode.SUBREDDIT_NAME_DUPLICATED);
        }

        // 이름 유효성 검사 (영문, 숫자, 언더스코어만 허용, 3-21자)
        if (!name.matches("^[a-zA-Z0-9_]{3,21}$")) {
            throw new CommunityException(ErrorCode.SUBREDDIT_NAME_INVALID);
        }

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        // 서브레딧 생성
        SubredditEntity subreddit = SubredditEntity.builder()
                .name(name.toLowerCase()) // 소문자로 통일
                .title(title)
                .description(description)
                .creator(creator)
                .isPrivate(isPrivate != null ? isPrivate : false)
                .isNsfw(isNsfw != null ? isNsfw : false)
                .build();

        SubredditEntity savedSubreddit = subredditRepository.save(subreddit);

        // 생성자를 FULL 권한 모더레이터로 추가
        SubredditModeratorEntity moderator = SubredditModeratorEntity.builder()
                .subreddit(savedSubreddit)
                .user(creator)
                .permission(SubredditModeratorEntity.ModeratorPermission.FULL)
                .build();
        moderatorRepository.save(moderator);

        // 생성자 자동 구독
        subscribeToSubreddit(savedSubreddit.getSubredditId(), userId);

        log.info("Subreddit created: name={}, creator={}", name, userId);
        return savedSubreddit;
    }

    /**
     * 서브레딧 조회 (이름으로)
     */
    @Cacheable(value = "subredditCache", key = "'name:' + #name", unless = "#result == null")
    public SubredditEntity getSubredditByName(String name) {
        return subredditRepository.findByNameAndIsActiveTrueAndIsDeletedFalse(name.toLowerCase())
                .orElseThrow(() -> new CommunityException(ErrorCode.SUBREDDIT_NOT_FOUND));
    }

    /**
     * 서브레딧 조회 (ID로)
     */
    @Cacheable(value = "subredditCache", key = "'id:' + #subredditId", unless = "#result == null")
    public SubredditEntity getSubredditById(Long subredditId) {
        return subredditRepository.findBySubredditIdAndIsActiveTrueAndIsDeletedFalse(subredditId)
                .orElseThrow(() -> new CommunityException(ErrorCode.SUBREDDIT_NOT_FOUND));
    }

    /**
     * 서브레딧 구독
     */
    @Transactional
    @DistributedLock(key = "'subreddit:subscribe:' + #subredditId + ':' + #userId", waitTime = 3, leaseTime = 2)
    @CacheEvict(value = "subredditCache", key = "'id:' + #subredditId")
    public void subscribeToSubreddit(Long subredditId, Long userId) {
        SubredditEntity subreddit = getSubredditById(subredditId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        // 이미 구독 중인지 확인
        if (subscriptionRepository.existsBySubredditAndUser(subreddit, user)) {
            throw new CommunityException(ErrorCode.SUBREDDIT_ALREADY_SUBSCRIBED);
        }

        // 구독 생성
        SubredditSubscriptionEntity subscription = SubredditSubscriptionEntity.builder()
                .subreddit(subreddit)
                .user(user)
                .build();
        subscriptionRepository.save(subscription);

        // 구독자 수 증가
        subreddit.increaseSubscriberCount();

        log.debug("User subscribed to subreddit: subreddit={}, user={}", subredditId, userId);
    }

    /**
     * 서브레딧 구독 취소
     */
    @Transactional
    @DistributedLock(key = "'subreddit:unsubscribe:' + #subredditId + ':' + #userId", waitTime = 3, leaseTime = 2)
    @CacheEvict(value = "subredditCache", key = "'id:' + #subredditId")
    public void unsubscribeFromSubreddit(Long subredditId, Long userId) {
        SubredditEntity subreddit = getSubredditById(subredditId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        // 구독 조회
        SubredditSubscriptionEntity subscription = subscriptionRepository.findBySubredditAndUser(subreddit, user)
                .orElseThrow(() -> new CommunityException(ErrorCode.SUBREDDIT_NOT_SUBSCRIBED));

        // 구독 삭제
        subscriptionRepository.delete(subscription);

        // 구독자 수 감소
        subreddit.decreaseSubscriberCount();

        log.debug("User unsubscribed from subreddit: subreddit={}, user={}", subredditId, userId);
    }

    /**
     * 구독 여부 확인
     */
    public boolean isSubscribed(Long subredditId, Long userId) {
        SubredditEntity subreddit = getSubredditById(subredditId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        return subscriptionRepository.existsBySubredditAndUser(subreddit, user);
    }

    /**
     * 사용자의 구독 목록 조회
     */
    public Page<SubredditEntity> getUserSubscriptions(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        return subscriptionRepository.findByUserOrderBySubscribedAtDesc(user, pageable)
                .map(SubredditSubscriptionEntity::getSubreddit);
    }

    /**
     * 인기 서브레딧 목록 (구독자 수 기준)
     */
    @Cacheable(value = "subredditListCache", key = "'popular:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<SubredditEntity> getPopularSubreddits(Pageable pageable) {
        return subredditRepository.findActiveSubredditsBySubscriberCount(pageable);
    }

    /**
     * 서브레딧 검색
     */
    public Page<SubredditEntity> searchSubreddits(String keyword, Pageable pageable) {
        return subredditRepository.searchSubreddits(keyword, pageable);
    }

    /**
     * 모더레이터 추가
     */
    @Transactional
    @CacheEvict(value = "subredditCache", key = "'id:' + #subredditId")
    public void addModerator(Long subredditId, Long userId, Long targetUserId,
                           SubredditModeratorEntity.ModeratorPermission permission) {
        SubredditEntity subreddit = getSubredditById(subredditId);

        // 권한 확인 (FULL 권한 모더레이터만 가능)
        if (!hasModeratorPermission(subredditId, userId, SubredditModeratorEntity.ModeratorPermission.FULL)) {
            throw new CommunityException(ErrorCode.SUBREDDIT_MODERATOR_PERMISSION_DENIED);
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        // 이미 모더레이터인지 확인
        if (moderatorRepository.existsBySubredditAndUser(subreddit, targetUser)) {
            throw new CommunityException(ErrorCode.SUBREDDIT_ALREADY_MODERATOR);
        }

        // 모더레이터 추가
        SubredditModeratorEntity moderator = SubredditModeratorEntity.builder()
                .subreddit(subreddit)
                .user(targetUser)
                .permission(permission)
                .build();
        moderatorRepository.save(moderator);

        log.info("Moderator added: subreddit={}, user={}, permission={}", subredditId, targetUserId, permission);
    }

    /**
     * 모더레이터 권한 확인
     */
    public boolean hasModeratorPermission(Long subredditId, Long userId,
                                         SubredditModeratorEntity.ModeratorPermission requiredPermission) {
        SubredditEntity subreddit = getSubredditById(subredditId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        return moderatorRepository.findBySubredditAndUser(subreddit, user)
                .map(moderator -> {
                    // FULL 권한은 모든 작업 가능
                    if (moderator.getPermission() == SubredditModeratorEntity.ModeratorPermission.FULL) {
                        return true;
                    }
                    // 요청된 권한과 일치하는지 확인
                    return moderator.getPermission() == requiredPermission;
                })
                .orElse(false);
    }

    /**
     * 서브레딧 모더레이터 목록
     */
    public List<SubredditModeratorEntity> getModerators(Long subredditId) {
        SubredditEntity subreddit = getSubredditById(subredditId);
        return moderatorRepository.findBySubredditOrderByAppointedAtAsc(subreddit);
    }

    /**
     * 서브레딧 수정
     */
    @Transactional
    @CacheEvict(value = {"subredditCache", "subredditListCache"}, allEntries = true)
    public SubredditEntity updateSubreddit(Long subredditId, Long userId, String title,
                                          String description, Boolean isPrivate, Boolean isNsfw) {
        SubredditEntity subreddit = getSubredditById(subredditId);

        // 권한 확인
        if (!hasModeratorPermission(subredditId, userId, SubredditModeratorEntity.ModeratorPermission.FULL)) {
            throw new CommunityException(ErrorCode.SUBREDDIT_MODERATOR_PERMISSION_DENIED);
        }

        // 수정
        subreddit.updateSubreddit(title, description, isPrivate, isNsfw);

        log.info("Subreddit updated: id={}, user={}", subredditId, userId);
        return subreddit;
    }

    /**
     * 서브레딧 비활성화 (삭제)
     */
    @Transactional
    @CacheEvict(value = {"subredditCache", "subredditListCache"}, allEntries = true)
    public void deactivateSubreddit(Long subredditId, Long userId) {
        SubredditEntity subreddit = getSubredditById(subredditId);

        // 생성자만 가능
        if (!subreddit.isCreator(userId)) {
            throw new CommunityException(ErrorCode.SUBREDDIT_MODERATOR_PERMISSION_DENIED);
        }

        subreddit.deactivate();

        log.info("Subreddit deactivated: id={}, user={}", subredditId, userId);
    }
}
