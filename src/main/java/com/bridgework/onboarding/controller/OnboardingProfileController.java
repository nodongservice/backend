package com.bridgework.onboarding.controller;

import com.bridgework.auth.exception.UnauthorizedException;
import com.bridgework.auth.security.UserPrincipal;
import com.bridgework.onboarding.dto.OnboardingProfileResponseDto;
import com.bridgework.onboarding.dto.OnboardingProfileUpsertRequestDto;
import com.bridgework.onboarding.service.OnboardingProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding", description = "이력 프로필 생성/조회 API")
@SecurityRequirement(name = "bearerAuth")
public class OnboardingProfileController {

    private final OnboardingProfileService onboardingProfileService;

    public OnboardingProfileController(OnboardingProfileService onboardingProfileService) {
        this.onboardingProfileService = onboardingProfileService;
    }

    @PutMapping("/profile")
    @Operation(summary = "온보딩 프로필 저장/수정", description = "필수/선택 입력값을 저장하고 AI 구조화 태그를 생성한다.")
    public ResponseEntity<OnboardingProfileResponseDto> upsertProfile(
            Authentication authentication,
            @Valid @RequestBody OnboardingProfileUpsertRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(onboardingProfileService.upsert(userId, request));
    }

    @GetMapping("/profile")
    @Operation(summary = "온보딩 프로필 조회", description = "현재 로그인 사용자의 온보딩 프로필을 조회한다.")
    public ResponseEntity<OnboardingProfileResponseDto> getProfile(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(onboardingProfileService.getByUserId(userId));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }
}
