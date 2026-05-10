package com.bridgework.profile.controller;

import com.bridgework.auth.exception.UnauthorizedException;
import com.bridgework.auth.security.UserPrincipal;
import com.bridgework.profile.dto.ProfilePortfolioExtractResponseDto;
import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import com.bridgework.profile.service.ProfilePortfolioDraftService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Profile", description = "프로필 생성/관리 API")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private static final String PROFILE_REQUIRED_REQUEST_EXAMPLE = "{\"fullName\":\"홍길동\",\"contactPhone\":\"010-1234-5678\",\"contactEmail\":\"user@example.com\",\"birthDate\":\"1990-01-01\",\"genderType\":\"MALE\",\"detailAddress\":\"강남구 테헤란로 123\",\"highestEducation\":\"BACHELOR\",\"graduationStatus\":\"GRADUATED\",\"majorCareer\":\"신입\",\"targetJob\":\"사무보조\",\"skills\":[\"엑셀\",\"문서작성\"],\"disabilityType\":\"PHYSICAL\",\"disabilitySeverity\":\"MODERATE\",\"disabilityRegisteredYn\":true,\"workAvailability\":\"IMMEDIATE\",\"workTypes\":[\"FULL_TIME\"],\"selfIntroduction\":\"꼼꼼한 문서 작업이 강점입니다.\"}";
    private static final String PROFILE_FULL_REQUEST_EXAMPLE = "{\"desiredJob\":\"데이터 라벨러\",\"commuteRange\":\"대중교통 50분 이내\",\"preferredWorkEnvironments\":[\"저소음\",\"엘리베이터 접근 용이\"],\"avoidedWorkEnvironments\":[\"장시간 서서 근무\"],\"requiredSupports\":[\"높이조절 책상\"],\"disabilityType\":\"PHYSICAL\",\"careerSummary\":\"사무지원 및 문서관리 4년\",\"educationSummary\":\"대학교 졸업\",\"employmentTypeSummary\":\"정규직 우선\",\"fullName\":\"홍길동\",\"contactPhone\":\"010-1234-5678\",\"contactEmail\":\"user@example.com\",\"birthDate\":\"1990-01-01\",\"genderType\":\"MALE\",\"ageGroup\":\"30대\",\"detailAddress\":\"강남구 테헤란로 123\",\"emergencyContact\":\"010-9999-8888\",\"highestEducation\":\"BACHELOR\",\"graduationStatus\":\"GRADUATED\",\"majorCareer\":\"사무보조 3년\",\"careerDetail\":\"고객응대 및 행정문서 정리 담당\",\"projectExperience\":\"민원 응대 프로세스 개선 프로젝트 참여\",\"careerGapReason\":\"재활 치료 후 복귀 준비\",\"targetJob\":\"사무보조\",\"skills\":[\"엑셀\",\"문서작성\",\"고객응대\"],\"certifications\":[\"컴퓨터활용능력 2급\",\"워드프로세서\"],\"portfolioUrl\":\"https://portfolio.example.com/hong\",\"awards\":\"구청 민원 서비스 개선 우수상\",\"trainings\":\"직업능력개발훈련 수료(사무행정)\",\"disabilitySeverity\":\"MODERATE\",\"disabilityRegisteredYn\":true,\"disabilityDescription\":\"장시간 보행이 어려워 좌식 위주 업무 선호\",\"assistiveDevices\":\"전동휠체어\",\"workSupportRequirements\":\"출입구 경사로와 자동문 필요\",\"workAvailability\":\"WITHIN_TWO_WEEKS\",\"workTypes\":[\"FULL_TIME\",\"CONTRACT\"],\"expectedSalary\":\"연봉 3200만원\",\"workTimePreference\":\"DAYTIME\",\"remoteAvailableYn\":true,\"mobilityRange\":\"8\",\"selfIntroduction\":\"정확한 문서 처리와 민원 응대에 강점이 있습니다.\",\"motivation\":\"장기적으로 안정적인 사무직 커리어를 이어가고 싶습니다.\",\"jobFitDescription\":\"행정업무 경험과 커뮤니케이션 역량으로 직무에 빠르게 적응 가능합니다.\",\"careerGoal\":\"3년 내 사무운영 담당자로 성장\",\"strengthsWeaknesses\":\"강점은 책임감, 약점은 완벽주의 성향\",\"militaryService\":\"NOT_APPLICABLE\",\"patrioticVeteranYn\":false,\"referrer\":\"장애인고용포털\",\"snsUrl\":\"https://www.linkedin.com/in/hong\"}";

    private final UserProfileService userProfileService;
    private final ProfilePortfolioDraftService profilePortfolioDraftService;

    public UserProfileController(UserProfileService userProfileService,
                                 ProfilePortfolioDraftService profilePortfolioDraftService) {
        this.userProfileService = userProfileService;
        this.profilePortfolioDraftService = profilePortfolioDraftService;
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
            content = @Content(examples = {
                    @ExampleObject(
                            name = "프로필 생성 요청(필수 입력 중심)",
                            value = PROFILE_REQUIRED_REQUEST_EXAMPLE
                    ),
                    @ExampleObject(
                            name = "프로필 생성 요청(필수+선택 입력 포함)",
                            value = PROFILE_FULL_REQUEST_EXAMPLE
                    )
            })
    )
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<UserProfileResponseDto>> createProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpsertRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(userProfileService.create(userId, request)));
    }

    @PostMapping(value = "/ocr/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "포트폴리오 OCR 기반 프로필 초안 생성",
            description = "PDF 파일을 FastAPI OCR+LLM으로 분석해 프로필 전체 항목 초안을 반환한다. 추출 근거가 없는 항목은 null로 반환한다."
    )
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<ProfilePortfolioExtractResponseDto>> extractProfileDraftFromPortfolio(
            Authentication authentication,
            @RequestPart("file") MultipartFile file
    ) {
        currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(profilePortfolioDraftService.extract(file)));
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
            content = @Content(examples = {
                    @ExampleObject(
                            name = "프로필 수정 요청(필수 입력 중심)",
                            value = PROFILE_REQUIRED_REQUEST_EXAMPLE
                    ),
                    @ExampleObject(
                            name = "프로필 수정 요청(필수+선택 입력 포함)",
                            value = PROFILE_FULL_REQUEST_EXAMPLE
                    )
            })
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
