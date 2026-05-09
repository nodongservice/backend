package com.bridgework.auth.controller;

import com.bridgework.auth.dto.AuthMeResponseDto;
import com.bridgework.auth.dto.LogoutRequestDto;
import com.bridgework.auth.dto.SignupCompleteRequestDto;
import com.bridgework.auth.dto.SocialLoginRequestDto;
import com.bridgework.auth.dto.SocialLoginResponseDto;
import com.bridgework.auth.dto.TokenPairResponseDto;
import com.bridgework.auth.dto.TokenRefreshRequestDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "소셜 로그인/회원가입/토큰 API")
public class AuthController {

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
                    content = @Content(examples = @ExampleObject(
                            name = "기존 회원 응답 예시",
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"signupRequired\":false,\"signupToken\":null,\"provider\":\"KAKAO\",\"email\":\"user@example.com\",\"name\":\"홍길동\",\"tokenPair\":{\"accessToken\":\"<ACCESS_TOKEN>\",\"refreshToken\":\"<REFRESH_TOKEN>\",\"tokenType\":\"Bearer\",\"accessTokenExpiresAt\":\"2026-05-08T08:00:00Z\",\"refreshTokenExpiresAt\":\"2026-05-22T08:00:00Z\"}}}"
                    )))
    })
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<SocialLoginResponseDto>> socialLogin(@Valid @RequestBody SocialLoginRequestDto request) {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(authService.socialLogin(request)));
    }

    @PostMapping("/social/signup/complete")
    @Operation(summary = "최초 회원가입 완료", description = "최초 소셜 로그인 후 필수 추가정보를 저장하고 가입을 완료한다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = @ExampleObject(
                    name = "회원가입 완료 요청 예시",
                    value = "{\"signupToken\":\"signup-token-sample\",\"email\":\"user@example.com\",\"profile\":{\"fullName\":\"홍길동\",\"contactPhone\":\"010-1234-5678\",\"contactEmail\":\"user@example.com\",\"birthDate\":\"1990-01-01\",\"residenceRegion\":\"서울\",\"detailAddress\":\"강남구\",\"highestEducation\":\"대졸\",\"graduationStatus\":\"졸업\",\"majorCareer\":\"사무보조 3년\",\"targetJob\":\"사무보조\",\"skills\":[\"엑셀\",\"문서작성\"],\"disabilityYn\":true,\"disabilityType\":\"지체장애\",\"disabilitySeverity\":\"중등도\",\"disabilityRegisteredYn\":true,\"workAvailability\":\"즉시\",\"workTypes\":[\"정규직\"],\"selfIntroduction\":\"꼼꼼한 문서 작업이 강점입니다.\",\"commuteRange\":\"대중교통 40분 이내\",\"preferredWorkEnvironments\":[\"저소음\"],\"avoidedWorkEnvironments\":[],\"requiredSupports\":[],\"careerSummary\":\"사무 경력\",\"educationSummary\":\"대졸\",\"employmentTypeSummary\":\"정규직\",\"motivation\":\"장기근속 희망\"}}"
            ))
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

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }
}
