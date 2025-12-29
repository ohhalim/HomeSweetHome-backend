package com.homesweet.homesweetback.domain.community.dto;

/**
 * 게시글 카운터 통합 DTO
 * 조회수, 좋아요수, 댓글수를 하나의 객체로 전달
 */
public record PostCounts(
        Integer viewCount,
        Integer likeCount,
        Integer commentCount) {
    /**
     * 모든 카운트를 0으로 초기화
     */
    public static PostCounts zero() {
        return new PostCounts(0, 0, 0);
    }

    /**
     * null 값을 0으로 대체한 안전한 인스턴스 생성
     */
    public static PostCounts ofNullSafe(Integer viewCount, Integer likeCount, Integer commentCount) {
        return new PostCounts(
                viewCount != null ? viewCount : 0,
                likeCount != null ? likeCount : 0,
                commentCount != null ? commentCount : 0);
    }
}
