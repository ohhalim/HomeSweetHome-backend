package com.homesweet.homesweetback.domain.product.product.command.repository;

import com.homesweet.homesweetback.domain.product.product.command.controller.response.ProductDetailResponse;
import com.homesweet.homesweetback.domain.product.product.command.controller.response.ProductManageResponse;
import com.homesweet.homesweetback.domain.product.product.command.controller.response.ProductPreviewResponse;
import com.homesweet.homesweetback.domain.product.product.command.controller.response.SkuStockResponse;
import com.homesweet.homesweetback.domain.product.product.command.domain.Product;
import com.homesweet.homesweetback.domain.product.product.command.domain.ProductStatus;

import java.util.List;
import java.util.Optional;

/**
 * 제품 레포 인터페이스
 *
 * @author junnukim1007gmail.com
 * @date 25. 10. 21.
 */
public interface ProductRepository {

    Product save(Product product);

    boolean existsById(Long productId);

    Optional<Product> findByIdAndSellerId(Long productId, Long sellerId);

    boolean existsBySellerIdAndName(Long sellerId, String name);

    List<SkuStockResponse> findSkuStocksByProductId(Long productId);

    ProductDetailResponse findProductDetailById(Long productId);

    List<ProductManageResponse> findProductsForSeller(Long sellerId, String startDate, String endDate);

    List<ProductPreviewResponse> findProductPreviews(Long categoryId, Long cursorId, int limit, String sortType);

    void updateStatus(Long productId, ProductStatus status);

    void update(Long productId, Product product);

    void updateMainImage(Long productId, String newImageUrl);

    void addDetailImages(Long productId, List<String> imageUrls);

    void deleteDetailImages(Long productId, List<String> imageUrls);

    // 알림용
    Product findByProductId(Long productId);
}