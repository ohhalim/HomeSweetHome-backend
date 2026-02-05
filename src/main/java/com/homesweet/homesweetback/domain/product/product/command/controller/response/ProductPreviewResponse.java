package com.homesweet.homesweetback.domain.product.product.command.controller.response;

import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.ProductEntity;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 상품 프리뷰 응답 DTO
 */
@Builder
public record ProductPreviewResponse(
    Long id,
    String name,
    String imageUrl,
    Integer basePrice,
    BigDecimal discountRate,
    Integer discountedPrice,
    Integer reviewCount,
    Double rating
) {
    public static ProductPreviewResponse from(ProductEntity entity) {
        Integer discounted = calculateDiscountedPrice(entity.getBasePrice(), entity.getDiscountRate());
        return ProductPreviewResponse.builder()
            .id(entity.getId())
            .name(entity.getName())
            .imageUrl(entity.getImageUrl())
            .basePrice(entity.getBasePrice())
            .discountRate(entity.getDiscountRate())
            .discountedPrice(discounted)
            .reviewCount(0)
            .rating(0.0)
            .build();
    }

    private static Integer calculateDiscountedPrice(Integer basePrice, BigDecimal discountRate) {
        if (basePrice == null || discountRate == null) {
            return basePrice;
        }
        BigDecimal rate = BigDecimal.ONE.subtract(discountRate.divide(BigDecimal.valueOf(100)));
        return rate.multiply(BigDecimal.valueOf(basePrice)).intValue();
    }
}

