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
import io.swagger.v3.oas.annotations.Operation;
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
    public ResponseEntity<SocialLoginResponseDto> socialLogin(@Valid @RequestBody SocialLoginRequestDto request) {
        return ResponseEntity.ok(authService.socialLogin(request));
    }

    @PostMapping("/social/signup/complete")
    @Operation(summary = "최초 회원가입 완료", description = "최초 소셜 로그인 후 필수 추가정보를 저장하고 가입을 완료한다.")
    public ResponseEntity<TokenPairResponseDto> completeSignup(@Valid @RequestBody SignupCompleteRequestDto request) {
        return ResponseEntity.ok(authService.completeSignup(request));
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "토큰 재발급", description = "리프레시 토큰 검증 후 액세스/리프레시 토큰을 재발급한다.")
    public ResponseEntity<TokenPairResponseDto> refreshToken(@Valid @RequestBody TokenRefreshRequestDto request) {
        return ResponseEntity.ok(authService.refreshToken(request.refreshToken()));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "로그아웃", description = "현재 사용자 리프레시 토큰을 무효화한다.")
    public ResponseEntity<Void> logout(Authentication authentication, @RequestBody(required = false) LogoutRequestDto request) {
        Long userId = currentUserId(authentication);
        String refreshToken = request == null ? null : request.refreshToken();
        authService.logout(userId, refreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 기본 정보를 조회한다.")
    public ResponseEntity<AuthMeResponseDto> me(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(authService.getMe(userId));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }
}
