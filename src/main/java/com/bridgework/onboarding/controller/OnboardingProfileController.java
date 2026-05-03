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
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Profile", description = "프로필 생성/관리 API")
@SecurityRequirement(name = "bearerAuth")
public class OnboardingProfileController {

    private final OnboardingProfileService onboardingProfileService;

    public OnboardingProfileController(OnboardingProfileService onboardingProfileService) {
        this.onboardingProfileService = onboardingProfileService;
    }

    @GetMapping
    @Operation(summary = "내 프로필 목록 조회", description = "현재 로그인 사용자의 프로필 목록을 조회한다. 기본 프로필이 먼저 반환된다.")
    public ResponseEntity<List<OnboardingProfileResponseDto>> getProfiles(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(onboardingProfileService.getProfiles(userId));
    }

    @PostMapping
    @Operation(summary = "프로필 생성", description = "프로필을 생성한다. 사용자당 최대 3개까지 생성할 수 있다.")
    public ResponseEntity<OnboardingProfileResponseDto> createProfile(
            Authentication authentication,
            @Valid @RequestBody OnboardingProfileUpsertRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(onboardingProfileService.create(userId, request));
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "프로필 단건 조회", description = "프로필 ID 기준으로 상세 정보를 조회한다.")
    public ResponseEntity<OnboardingProfileResponseDto> getProfile(
            Authentication authentication,
            @PathVariable("profileId") Long profileId
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(onboardingProfileService.getProfile(userId, profileId));
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "프로필 수정", description = "프로필을 수정하고 AI 구조화 태그를 다시 생성한다.")
    public ResponseEntity<OnboardingProfileResponseDto> updateProfile(
            Authentication authentication,
            @PathVariable("profileId") Long profileId,
            @Valid @RequestBody OnboardingProfileUpsertRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(onboardingProfileService.update(userId, profileId, request));
    }

    @DeleteMapping("/{profileId}")
    @Operation(summary = "프로필 삭제", description = "기본 프로필이 아니고, 마지막 1개가 아닌 경우에만 삭제할 수 있다.")
    public ResponseEntity<Void> deleteProfile(
            Authentication authentication,
            @PathVariable("profileId") Long profileId
    ) {
        Long userId = currentUserId(authentication);
        onboardingProfileService.delete(userId, profileId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{profileId}/set-default")
    @Operation(summary = "기본 프로필 지정", description = "선택한 프로필을 기본 프로필로 변경한다.")
    public ResponseEntity<OnboardingProfileResponseDto> setDefaultProfile(
            Authentication authentication,
            @PathVariable("profileId") Long profileId
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(onboardingProfileService.setDefault(userId, profileId));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }
}
