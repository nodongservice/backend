package com.bridgework.admin.dummy.controller;

import com.bridgework.admin.dummy.dto.AdminDummyCaseResponseDto;
import com.bridgework.admin.dummy.dto.AdminDummyLoginRequestDto;
import com.bridgework.admin.dummy.dto.AdminDummyLoginResponseDto;
import com.bridgework.admin.dummy.service.AdminDummyAuthService;
import com.bridgework.auth.exception.UnauthorizedException;
import com.bridgework.auth.security.UserPrincipal;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dummy-auth")
@Tag(name = "AdminDummyAuth", description = "관리자 더미 사용자 인증 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminDummyAuthController {

    private final AdminDummyAuthService adminDummyAuthService;

    public AdminDummyAuthController(AdminDummyAuthService adminDummyAuthService) {
        this.adminDummyAuthService = adminDummyAuthService;
    }

    @GetMapping("/cases")
    @Operation(summary = "더미 사용자 케이스 목록 조회", description = "추천 게이트웨이 테스트용 더미 사용자/프로필 케이스 목록을 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":[{\"dummyKey\":\"case-office-rookie\",\"displayName\":\"사무지원 신입형\",\"scenarioSummary\":\"필수 중심 입력 케이스\",\"profiles\":[{\"profileId\":1,\"profileKey\":\"office-default\",\"profileLabel\":\"사무신입 기본형\",\"scenarioSummary\":\"필수 입력 위주\",\"isDefault\":true}]}]}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<List<AdminDummyCaseResponseDto>>> getCases() {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(adminDummyAuthService.getActiveCases()));
    }

    @PostMapping("/login")
    @Operation(summary = "더미 사용자 로그인", description = "관리자 권한으로 선택한 더미 사용자의 Access 토큰을 발급한다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = @ExampleObject(value = "{\"dummyKey\":\"case-office-rookie\"}"))
    )
    @ApiResponse(responseCode = "200", description = "로그인 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"accessToken\":\"<DUMMY_USER_ACCESS_TOKEN>\",\"refreshToken\":\"<DUMMY_USER_REFRESH_TOKEN>\",\"tokenType\":\"Bearer\",\"accessTokenExpiresAt\":\"2026-05-08T08:00:00Z\",\"refreshTokenExpiresAt\":\"2026-05-22T08:00:00Z\",\"userId\":6,\"dummyKey\":\"case-office-rookie\",\"profiles\":[{\"profileId\":3,\"profileKey\":\"office-default\",\"profileLabel\":\"사무신입 기본형\",\"scenarioSummary\":\"필수 입력 위주 기본 프로필\",\"isDefault\":true}]}}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<AdminDummyLoginResponseDto>> loginAsDummyUser(
            Authentication authentication,
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody AdminDummyLoginRequestDto request
    ) {
        Long adminUserId = currentUserId(authentication);
        String requestIp = resolveRequestIp(httpServletRequest);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(
                adminDummyAuthService.loginAsDummyUser(adminUserId, requestIp, request)
        ));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }

    private String resolveRequestIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
