package com.homesweet.homesweetback.domain.community.controller;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2UserPrincipal;
import com.homesweet.homesweetback.domain.community.dto.KarmaResponse;
import com.homesweet.homesweetback.domain.community.service.KarmaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Karma Controller
 *
 * Reddit-style 카르마(사용자 신뢰도) API
 *
 * @author ohhalim777@gmail.com
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Karma", description = "카르마 (사용자 신뢰도) API")
public class KarmaController {

    private final KarmaService karmaService;

    /**
     * 사용자 카르마 조회
     */
    @GetMapping("/users/{userId}/karma")
    @Operation(
        summary = "사용자 카르마 조회",
        description = "사용자의 카르마 상세 정보를 조회합니다 (Post Karma, Comment Karma, Level 포함)"
    )
    public ResponseEntity<KarmaResponse> getUserKarma(
            @PathVariable @Parameter(description = "사용자 ID") Long userId) {

        KarmaService.KarmaDetail detail = karmaService.getKarmaDetail(userId);
        KarmaService.KarmaLevel level = karmaService.getKarmaLevel(userId);

        return ResponseEntity.ok(KarmaResponse.from(detail, level));
    }

    /**
     * 내 카르마 조회
     */
    @GetMapping("/users/me/karma")
    @Operation(summary = "내 카르마 조회", description = "현재 로그인한 사용자의 카르마를 조회합니다")
    public ResponseEntity<KarmaResponse> getMyKarma(Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        KarmaService.KarmaDetail detail = karmaService.getKarmaDetail(userId);
        KarmaService.KarmaLevel level = karmaService.getKarmaLevel(userId);

        return ResponseEntity.ok(KarmaResponse.from(detail, level));
    }

    /**
     * 총 카르마 조회 (간단)
     */
    @GetMapping("/users/{userId}/karma/total")
    @Operation(summary = "총 카르마 조회", description = "사용자의 총 카르마만 간단히 조회합니다")
    public ResponseEntity<Long> getTotalKarma(
            @PathVariable @Parameter(description = "사용자 ID") Long userId) {

        long totalKarma = karmaService.getTotalKarma(userId);
        return ResponseEntity.ok(totalKarma);
    }

    /**
     * 카르마 캐시 무효화 (관리자용)
     */
    @DeleteMapping("/users/{userId}/karma/cache")
    @Operation(summary = "카르마 캐시 무효화", description = "사용자의 카르마 캐시를 무효화합니다 (관리자 전용)")
    public ResponseEntity<Void> invalidateKarmaCache(
            @PathVariable @Parameter(description = "사용자 ID") Long userId) {

        karmaService.invalidateKarmaCache(userId);
        return ResponseEntity.noContent().build();
    }
}
