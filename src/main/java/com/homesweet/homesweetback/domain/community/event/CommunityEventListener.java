package com.homesweet.homesweetback.domain.community.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커뮤니티 이벤트 리스너
 *
 * 각 이벤트에 대한 비동기 후속 처리를 담당합니다:
 * - 알림 전송
 * - 통계 업데이트  
 * - 로그 기록
 * - 외부 시스템 연동
 *
 * @author ohhalim777@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityEventListener {

    /**
     * 게시글 생성 이벤트 처리
     * 
     * 트랜잭션 커밋 후 실행되며, 다음 작업을 수행:
     * - 게시글 생성 로그 기록
     * - 통계 업데이트 (선택적)
     * - 검색 인덱스 업데이트 (추후 ElasticSearch 연동 시)
     */
    @Async("communityEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostCreated(PostCreatedEvent event) {
        try {
            log.info("Post created - postId: {}, userId: {}, title: {}, category: {}", 
                event.getPostId(), event.getUserId(), event.getTitle(), event.getCategory());
            
            // TODO: 추가 처리 (통계, 검색 인덱스 등)
            // - 카테고리별 게시글 수 통계 업데이트
            // - ElasticSearch 인덱싱 (전문 검색용)
            // - 추천 시스템 데이터 업데이트
            
        } catch (Exception e) {
            log.error("Failed to handle PostCreatedEvent: {}", event.getPostId(), e);
        }
    }

    /**
     * 게시글 좋아요 이벤트 처리
     * 
     * 트랜잭션 커밋 후 실행되며, 다음 작업을 수행:
     * - 작성자에게 알림 전송
     * - 인기 게시글 통계 업데이트
     */
    @Async("communityEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostLiked(PostLikedEvent event) {
        try {
            if (event.isLiked()) {
                log.info("Post liked - postId: {}, userId: {}", event.getPostId(), event.getUserId());
                
                // TODO: 추가 처리
                // - 작성자에게 좋아요 알림 전송 (이미 CommunityCountService에서 처리 중)
                // - 인기 게시글 랭킹 업데이트
                // - 사용자 활동 로그 기록
                
            } else {
                log.info("Post unliked - postId: {}, userId: {}", event.getPostId(), event.getUserId());
            }
        } catch (Exception e) {
            log.error("Failed to handle PostLikedEvent: {}", event.getPostId(), e);
        }
    }

    /**
     * 댓글 생성 이벤트 처리
     * 
     * 트랜잭션 커밋 후 실행되며, 다음 작업을 수행:
     * - 게시글 작성자에게 알림 전송
     * - 부모 댓글 작성자에게 알림 전송 (대댓글인 경우)
     * - 댓글 수 통계 업데이트
     */
    @Async("communityEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommentCreated(CommentCreatedEvent event) {
        try {
            log.info("Comment created - commentId: {}, postId: {}, userId: {}, parentCommentId: {}", 
                event.getCommentId(), event.getPostId(), event.getUserId(), event.getParentCommentId());
            
            // TODO: 추가 처리
            // - 게시글 작성자에게 새 댓글 알림
            // - 대댓글인 경우 부모 댓글 작성자에게 알림
            // - 활발한 게시글 통계 업데이트
            
        } catch (Exception e) {
            log.error("Failed to handle CommentCreatedEvent: {}", event.getCommentId(), e);
        }
    }
}
