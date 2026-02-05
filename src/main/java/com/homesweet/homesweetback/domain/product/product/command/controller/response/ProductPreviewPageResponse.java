package com.homesweet.homesweetback.domain.product.product.command.controller.response;

import java.util.List;

/**
 * 상품 프리뷰 페이지 응답 DTO
 */
public record ProductPreviewPageResponse(
    List<ProductPreviewResponse> contents,
    Long nextCursorId,
    boolean hasNext
) {
    public static ProductPreviewPageResponse of(List<ProductPreviewResponse> contents, int limit) {
        boolean hasNext = contents.size() > limit;
        List<ProductPreviewResponse> trimmed = hasNext ? contents.subList(0, limit) : contents;
        Long nextCursor = hasNext && !trimmed.isEmpty() 
            ? trimmed.get(trimmed.size() - 1).id() 
            : null;
        return new ProductPreviewPageResponse(trimmed, nextCursor, hasNext);
    }
}
