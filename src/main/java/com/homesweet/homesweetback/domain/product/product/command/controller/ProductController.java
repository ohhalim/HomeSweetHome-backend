package com.homesweet.homesweetback.domain.product.product.command.controller;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2UserPrincipal;
import com.homesweet.homesweetback.domain.product.product.command.controller.request.create.ProductUploadRequest;
import com.homesweet.homesweetback.domain.product.product.command.controller.request.update.ProductBasicInfoUpdateRequest;
import com.homesweet.homesweetback.domain.product.product.command.controller.request.update.ProductImageUpdateRequest;
import com.homesweet.homesweetback.domain.product.product.command.controller.request.update.ProductSkuUpdateRequest;
import com.homesweet.homesweetback.domain.product.product.command.controller.request.update.ProductStatusUpdateRequest;
import com.homesweet.homesweetback.domain.product.product.command.controller.response.*;
import com.homesweet.homesweetback.domain.product.product.command.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * 제품 컨트롤러
 * /api/v1/products POST 상품 등록
 * /api/v1/products/previews GET 상품 프리뷰 목록 조회
 * /api/v1/products/{productId} GET - 상품 상세 보기
 * /api/v1/products/{productId}/stocks GET - 상품 옵션 별 재고 보기 (단일 제품)
 * /api/v1/products/seller GET - 판매자 판매 상품 목록 조회
 *
 * @author junnukim1007gmail.com
 * @date 25. 10. 21.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;

    // [판매자] - 상품 등록
    @PostMapping
    public ResponseEntity<ProductResponse> registerProduct(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @Valid @ModelAttribute ProductUploadRequest request
    ) {

        Long sellerId = principal.getUserId();

        ProductResponse response = service.registerProduct(sellerId, request.product(), request.mainImage(), request.detailImages());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // [모두] 상품 프리뷰 목록 조회 (무한 스크롤)
    @GetMapping("/previews")
    public ResponseEntity<ProductPreviewPageResponse> getProductPreviews(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(defaultValue = "LATEST") String sortType
    ) {
        ProductPreviewPageResponse response = service.getProductPreviews(categoryId, cursorId, limit, sortType);
        return ResponseEntity.ok(response);
    }

    // [모두] 제품 상세 페이지 조회
    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(@PathVariable Long productId) {

        ProductDetailResponse response = service.getProductDetail(productId);

        return ResponseEntity.ok(response);
    }

    // [모두] 제품 옵션 별 재고 조회 -> 무옵션, 단일 옵션, 다중 옵션마다 달라야 할까?
    @GetMapping("/{productId}/stocks")
    public ResponseEntity<List<SkuStockResponse>> getProductStock(@PathVariable Long productId) {
        List<SkuStockResponse> response = service.getProductStock(productId);

        return ResponseEntity.ok(response);
    }

    // [판매자] 판매 물품 조회
    @GetMapping("/seller")
    public ResponseEntity<List<ProductManageResponse>> getSellerProducts(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {

        Long sellerId = principal.getUserId();

        List<ProductManageResponse> response = service.getSellerProducts(sellerId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{productId}/status")
    public ResponseEntity<Void> updateProductStatus(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        Long sellerId = principal.getUserId();
        log.info("userId = {}", sellerId);
        service.updateProductStatus(sellerId, productId, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{productId}/skus")
    public ResponseEntity<Void> updateSkuStock(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody ProductSkuUpdateRequest request
    ) {
        Long sellerId = principal.getUserId();
        service.updateSkuStock(sellerId, productId, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<Void> updateBasicInfo(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody ProductBasicInfoUpdateRequest request
    ) {
        Long sellerId = principal.getUserId();

        service.updateBasicInfo(sellerId, productId, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{productId}/images")
    public ResponseEntity<Void> updateProductImages(
            @AuthenticationPrincipal OAuth2UserPrincipal principal,
            @PathVariable Long productId,
            @Valid @ModelAttribute ProductImageUpdateRequest request
    ) {
        Long sellerId = principal.getUserId();
        service.updateImages(sellerId, productId, request);
        return ResponseEntity.ok().build();
    }
}
