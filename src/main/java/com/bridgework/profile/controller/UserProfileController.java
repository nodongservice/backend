package com.bridgework.profile.controller;

import com.bridgework.auth.exception.UnauthorizedException;
import com.bridgework.auth.security.UserPrincipal;
import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import com.bridgework.profile.service.UserProfileService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    @Operation(summary = "내 프로필 목록 조회", description = "현재 로그인 사용자의 프로필 목록을 조회한다. 기본 프로필이 먼저 반환된다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":[{\"profileId\":3,\"userId\":6,\"isDefault\":true,\"fullName\":\"박지훈(더미-상담기본)\",\"targetJob\":\"고객상담\",\"skills\":[\"고객응대\",\"CRM\",\"문제해결\"],\"disabilityType\":\"청각장애\",\"disabilitySeverity\":\"중등도\",\"disabilityRegisteredYn\":true,\"workTypes\":[\"정규직\"],\"selfIntroduction\":\"고객 감정 완화와 문제 해결에 자신 있습니다.\"}]}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<List<UserProfileResponseDto>>> getProfiles(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(userProfileService.getProfiles(userId)));
    }

    @PostMapping
    @Operation(summary = "프로필 생성", description = "프로필을 생성한다. 사용자당 최대 3개까지 생성할 수 있다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = @ExampleObject(
                    value = "{\"fullName\":\"홍길동\",\"contactPhone\":\"010-1234-5678\",\"contactEmail\":\"user@example.com\",\"birthDate\":\"1990-01-01\",\"residenceRegion\":\"서울\",\"detailAddress\":\"강남구\",\"highestEducation\":\"대졸\",\"graduationStatus\":\"졸업\",\"majorCareer\":\"사무보조 3년\",\"targetJob\":\"사무보조\",\"skills\":[\"엑셀\",\"문서작성\"],\"disabilityYn\":true,\"disabilityType\":\"지체장애\",\"disabilitySeverity\":\"중등도\",\"disabilityRegisteredYn\":true,\"workAvailability\":\"즉시\",\"workTypes\":[\"정규직\"],\"selfIntroduction\":\"꼼꼼한 문서 작업이 강점입니다.\",\"commuteRange\":\"대중교통 40분 이내\",\"preferredWorkEnvironments\":[\"저소음\"],\"avoidedWorkEnvironments\":[],\"requiredSupports\":[],\"careerSummary\":\"사무 경력\",\"educationSummary\":\"대졸\",\"employmentTypeSummary\":\"정규직\",\"motivation\":\"장기근속 희망\"}"
            ))
    )
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<UserProfileResponseDto>> createProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpsertRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(userProfileService.create(userId, request)));
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "프로필 단건 조회", description = "프로필 ID 기준으로 상세 정보를 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"profileId\":3,\"userId\":6,\"isDefault\":true,\"fullName\":\"박지훈(더미-상담기본)\",\"targetJob\":\"고객상담\",\"skills\":[\"고객응대\",\"CRM\",\"문제해결\"],\"disabilityType\":\"청각장애\",\"disabilitySeverity\":\"중등도\",\"disabilityRegisteredYn\":true,\"workTypes\":[\"정규직\"],\"selfIntroduction\":\"고객 감정 완화와 문제 해결에 자신 있습니다.\"}}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<UserProfileResponseDto>> getProfile(
            Authentication authentication,
            @PathVariable("profileId") Long profileId
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(userProfileService.getProfile(userId, profileId)));
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "프로필 수정", description = "프로필을 수정하고 AI 구조화 태그를 다시 생성한다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = @ExampleObject(
                    value = "{\"fullName\":\"홍길동\",\"contactPhone\":\"010-1234-5678\",\"contactEmail\":\"user@example.com\",\"birthDate\":\"1990-01-01\",\"residenceRegion\":\"서울\",\"detailAddress\":\"강남구\",\"highestEducation\":\"대졸\",\"graduationStatus\":\"졸업\",\"majorCareer\":\"사무보조 4년\",\"targetJob\":\"사무보조\",\"skills\":[\"엑셀\",\"문서작성\",\"OA\"],\"disabilityYn\":true,\"disabilityType\":\"지체장애\",\"disabilitySeverity\":\"중등도\",\"disabilityRegisteredYn\":true,\"workAvailability\":\"즉시\",\"workTypes\":[\"정규직\"],\"selfIntroduction\":\"협업과 문서 정리에 강점이 있습니다.\",\"commuteRange\":\"대중교통 40분 이내\",\"preferredWorkEnvironments\":[\"저소음\"],\"avoidedWorkEnvironments\":[],\"requiredSupports\":[],\"careerSummary\":\"사무 경력\",\"educationSummary\":\"대졸\",\"employmentTypeSummary\":\"정규직\",\"motivation\":\"장기근속 희망\"}"
            ))
    )
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<UserProfileResponseDto>> updateProfile(
            Authentication authentication,
            @PathVariable("profileId") Long profileId,
            @Valid @RequestBody UserProfileUpsertRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(userProfileService.update(userId, profileId, request)));
    }

    @DeleteMapping("/{profileId}")
    @Operation(summary = "프로필 삭제", description = "기본 프로필이 아니고, 마지막 1개가 아닌 경우에만 삭제할 수 있다.")
    @ApiResponse(responseCode = "200", description = "삭제 성공")
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<Void>> deleteProfile(
            Authentication authentication,
            @PathVariable("profileId") Long profileId
    ) {
        Long userId = currentUserId(authentication);
        userProfileService.delete(userId, profileId);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(null));
    }

    @PatchMapping("/{profileId}/set-default")
    @Operation(summary = "기본 프로필 지정", description = "선택한 프로필을 기본 프로필로 변경한다.")
    @ApiResponse(responseCode = "200", description = "기본 프로필 변경 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"profileId\":4,\"userId\":6,\"isDefault\":true,\"fullName\":\"박지훈(더미-QA전환)\",\"targetJob\":\"고객지원 QA\",\"skills\":[\"품질관리\",\"데이터분석\",\"커뮤니케이션\"]}}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<UserProfileResponseDto>> setDefaultProfile(
            Authentication authentication,
            @PathVariable("profileId") Long profileId
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(userProfileService.setDefault(userId, profileId)));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }
}
