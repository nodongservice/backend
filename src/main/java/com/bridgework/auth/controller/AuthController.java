package com.bridgework.auth.controller;

import com.bridgework.auth.dto.AuthMeResponseDto;
import com.bridgework.auth.dto.LogoutRequestDto;
import com.bridgework.auth.dto.SignupCompleteRequestDto;
import com.bridgework.auth.dto.SocialLoginRequestDto;
import com.bridgework.auth.dto.SocialLoginResponseDto;
import com.bridgework.auth.dto.TokenPairResponseDto;
import com.bridgework.auth.dto.TokenRefreshRequestDto;
import com.bridgework.auth.dto.WithdrawCancelRequestDto;
import com.bridgework.auth.exception.UnauthorizedException;
import com.bridgework.auth.security.UserPrincipal;
import com.bridgework.auth.service.AuthService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "소셜 로그인/회원가입/토큰 API")
public class AuthController {

    private static final String SIGNUP_COMPLETE_REQUIRED_REQUEST_EXAMPLE = "{\"signupToken\":\"signup-token-sample\",\"profile\":{\"fullName\":\"홍길동\",\"contactPhone\":\"010-1234-5678\",\"contactEmail\":\"user@example.com\",\"birthDate\":\"1990-01-01\",\"genderType\":\"MALE\",\"detailAddress\":\"강남구 테헤란로 123\",\"highestEducation\":\"BACHELOR\",\"graduationStatus\":\"GRADUATED\",\"majorCareer\":\"신입\",\"targetJob\":\"사무보조\",\"skills\":[\"엑셀\",\"문서작성\"],\"disabilityType\":\"PHYSICAL\",\"disabilitySeverity\":\"MODERATE\",\"disabilityRegisteredYn\":true,\"workAvailability\":\"IMMEDIATE\",\"workTypes\":[\"FULL_TIME\"],\"selfIntroduction\":\"꼼꼼한 문서 작업이 강점입니다.\"}}";
    private static final String SIGNUP_COMPLETE_FULL_REQUEST_EXAMPLE = "{\"signupToken\":\"signup-token-sample\",\"profile\":{\"desiredJob\":\"데이터 라벨러\",\"commuteRange\":\"대중교통 50분 이내\",\"preferredWorkEnvironments\":[\"저소음\",\"엘리베이터 접근 용이\"],\"avoidedWorkEnvironments\":[\"장시간 서서 근무\"],\"requiredSupports\":[\"높이조절 책상\"],\"disabilityType\":\"PHYSICAL\",\"careerSummary\":\"사무지원 및 문서관리 4년\",\"educationSummary\":\"대학교 졸업\",\"employmentTypeSummary\":\"정규직 우선\",\"fullName\":\"홍길동\",\"contactPhone\":\"010-1234-5678\",\"contactEmail\":\"user@example.com\",\"birthDate\":\"1990-01-01\",\"genderType\":\"MALE\",\"ageGroup\":\"30대\",\"detailAddress\":\"강남구 테헤란로 123\",\"emergencyContact\":\"010-9999-8888\",\"highestEducation\":\"BACHELOR\",\"graduationStatus\":\"GRADUATED\",\"majorCareer\":\"사무보조 3년\",\"careerDetail\":\"고객응대 및 행정문서 정리 담당\",\"projectExperience\":\"민원 응대 프로세스 개선 프로젝트 참여\",\"careerGapReason\":\"재활 치료 후 복귀 준비\",\"targetJob\":\"사무보조\",\"skills\":[\"엑셀\",\"문서작성\",\"고객응대\"],\"certifications\":[\"컴퓨터활용능력 2급\",\"워드프로세서\"],\"portfolioUrl\":\"https://portfolio.example.com/hong\",\"awards\":\"구청 민원 서비스 개선 우수상\",\"trainings\":\"직업능력개발훈련 수료(사무행정)\",\"disabilitySeverity\":\"MODERATE\",\"disabilityRegisteredYn\":true,\"disabilityDescription\":\"장시간 보행이 어려워 좌식 위주 업무 선호\",\"assistiveDevices\":\"전동휠체어\",\"workSupportRequirements\":\"출입구 경사로와 자동문 필요\",\"workAvailability\":\"WITHIN_TWO_WEEKS\",\"workTypes\":[\"FULL_TIME\",\"CONTRACT\"],\"expectedSalary\":\"연봉 3200만원\",\"workTimePreference\":\"DAYTIME\",\"remoteAvailableYn\":true,\"mobilityRange\":\"8\",\"selfIntroduction\":\"정확한 문서 처리와 민원 응대에 강점이 있습니다.\",\"motivation\":\"장기적으로 안정적인 사무직 커리어를 이어가고 싶습니다.\",\"jobFitDescription\":\"행정업무 경험과 커뮤니케이션 역량으로 직무에 빠르게 적응 가능합니다.\",\"careerGoal\":\"3년 내 사무운영 담당자로 성장\",\"strengthsWeaknesses\":\"강점은 책임감, 약점은 완벽주의 성향\",\"militaryService\":\"NOT_APPLICABLE\",\"patrioticVeteranYn\":false,\"referrer\":\"장애인고용포털\",\"snsUrl\":\"https://www.linkedin.com/in/hong\"}}";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/social/login")
    @Operation(summary = "소셜 로그인", description = "카카오/네이버 인가코드로 로그인하고 최초 로그인 시 추가정보 입력 토큰을 발급한다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = @ExampleObject(
                    name = "소셜 로그인 요청 예시",
                    value = "{\"provider\":\"KAKAO\",\"code\":\"oauth-code-sample\",\"redirectUri\":\"https://bridgework.cloud/auth/kakao/callback\"}"
            ))
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "기존 회원 응답 예시",
                                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"signupRequired\":false,\"signupToken\":null,\"provider\":\"KAKAO\",\"email\":\"user@example.com\",\"name\":\"홍길동\",\"accountStatus\":\"ACTIVE\",\"withdrawalDeadlineAt\":null,\"withdrawalCancelToken\":null,\"tokenPair\":{\"accessToken\":\"<ACCESS_TOKEN>\",\"refreshToken\":\"<REFRESH_TOKEN>\",\"tokenType\":\"Bearer\",\"accessTokenExpiresAt\":\"2026-05-08T08:00:00Z\",\"refreshTokenExpiresAt\":\"2026-05-22T08:00:00Z\"}}}"
                            ),
                            @ExampleObject(
                                    name = "최초 회원 응답 예시",
                                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"signupRequired\":true,\"signupToken\":\"signup-token-sample\",\"provider\":\"KAKAO\",\"email\":\"first-user@example.com\",\"name\":\"홍길동\",\"accountStatus\":\"SIGNUP_REQUIRED\",\"withdrawalDeadlineAt\":null,\"withdrawalCancelToken\":null,\"tokenPair\":null}}"
                            ),
                            @ExampleObject(
                                    name = "탈퇴 신청 상태 회원 응답 예시",
                                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"signupRequired\":false,\"signupToken\":null,\"provider\":\"KAKAO\",\"email\":\"user@example.com\",\"name\":\"홍길동\",\"accountStatus\":\"PENDING_DELETION\",\"withdrawalDeadlineAt\":\"2026-06-09T03:00:00Z\",\"withdrawalCancelToken\":\"withdraw-cancel-token-sample\",\"tokenPair\":null}}"
                            )
                    }))
    })
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<SocialLoginResponseDto>> socialLogin(@Valid @RequestBody SocialLoginRequestDto request) {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(authService.socialLogin(request)));
    }

    @PostMapping("/social/signup/complete")
    @Operation(summary = "최초 회원가입 완료", description = "최초 소셜 로그인 후 필수 추가정보를 저장하고 가입을 완료한다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = {
                    @ExampleObject(
                            name = "회원가입 완료 요청(필수 입력 중심)",
                            value = SIGNUP_COMPLETE_REQUIRED_REQUEST_EXAMPLE
                    ),
                    @ExampleObject(
                            name = "회원가입 완료 요청(필수+선택 입력 포함)",
                            value = SIGNUP_COMPLETE_FULL_REQUEST_EXAMPLE
                    )
            })
    )
    @ApiResponse(responseCode = "200", description = "가입 완료 및 토큰 발급",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"accessToken\":\"<ACCESS_TOKEN>\",\"refreshToken\":\"<REFRESH_TOKEN>\",\"tokenType\":\"Bearer\",\"accessTokenExpiresAt\":\"2026-05-08T08:00:00Z\",\"refreshTokenExpiresAt\":\"2026-05-22T08:00:00Z\"}}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<TokenPairResponseDto>> completeSignup(@Valid @RequestBody SignupCompleteRequestDto request) {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(authService.completeSignup(request)));
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "토큰 재발급", description = "리프레시 토큰 검증 후 액세스/리프레시 토큰을 재발급한다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = @ExampleObject(value = "{\"refreshToken\":\"<REFRESH_TOKEN>\"}"))
    )
    @ApiResponse(responseCode = "200", description = "토큰 재발급 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"accessToken\":\"<NEW_ACCESS_TOKEN>\",\"refreshToken\":\"<NEW_REFRESH_TOKEN>\",\"tokenType\":\"Bearer\",\"accessTokenExpiresAt\":\"2026-05-08T08:15:00Z\",\"refreshTokenExpiresAt\":\"2026-05-22T08:15:00Z\"}}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<TokenPairResponseDto>> refreshToken(@Valid @RequestBody TokenRefreshRequestDto request) {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(authService.refreshToken(request.refreshToken())));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "로그아웃", description = "현재 사용자 리프레시 토큰을 무효화한다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = false,
            content = @Content(examples = @ExampleObject(value = "{\"refreshToken\":\"<REFRESH_TOKEN>\"}"))
    )
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<Void>> logout(Authentication authentication, @RequestBody(required = false) LogoutRequestDto request) {
        Long userId = currentUserId(authentication);
        String refreshToken = request == null ? null : request.refreshToken();
        authService.logout(userId, refreshToken);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(null));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 기본 정보를 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"userId\":6,\"provider\":\"KAKAO\",\"email\":\"dummy.service.1@bridgework.local\",\"role\":\"USER\",\"signupCompleted\":true}}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<AuthMeResponseDto>> me(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(authService.getMe(userId)));
    }

    @DeleteMapping("/withdraw")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "회원 탈퇴 신청", description = "회원 상태를 탈퇴 신청으로 전환하고 30일 후 최종 탈퇴 처리 대상으로 등록한다.")
    @ApiResponse(responseCode = "200", description = "탈퇴 신청 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":null}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<Void>> withdraw(Authentication authentication) {
        Long userId = currentUserId(authentication);
        authService.withdraw(userId);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(null));
    }

    @PostMapping("/withdraw/cancel")
    @Operation(summary = "회원 탈퇴 신청 취소", description = "탈퇴 신청 상태 계정을 활성화로 복구하고 토큰을 재발급한다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = @ExampleObject(
                    value = "{\"withdrawalCancelToken\":\"withdraw-cancel-token-sample\"}"
            ))
    )
    @ApiResponse(responseCode = "200", description = "탈퇴 취소 및 토큰 재발급 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"accessToken\":\"<ACCESS_TOKEN>\",\"refreshToken\":\"<REFRESH_TOKEN>\",\"tokenType\":\"Bearer\",\"accessTokenExpiresAt\":\"2026-05-08T08:00:00Z\",\"refreshTokenExpiresAt\":\"2026-05-22T08:00:00Z\"}}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<TokenPairResponseDto>> cancelWithdraw(@Valid @RequestBody WithdrawCancelRequestDto request) {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(
                authService.cancelWithdrawal(request.withdrawalCancelToken())
        ));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }
}
