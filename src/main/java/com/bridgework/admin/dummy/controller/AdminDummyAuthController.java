package com.bridgework.admin.dummy.controller;

import com.bridgework.admin.dummy.dto.AdminDummyCaseResponseDto;
import com.bridgework.admin.dummy.dto.AdminDummyLoginRequestDto;
import com.bridgework.admin.dummy.dto.AdminDummyLoginResponseDto;
import com.bridgework.admin.dummy.service.AdminDummyAuthService;
import com.bridgework.auth.exception.UnauthorizedException;
import com.bridgework.auth.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
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
    public ResponseEntity<List<AdminDummyCaseResponseDto>> getCases() {
        return ResponseEntity.ok(adminDummyAuthService.getActiveCases());
    }

    @PostMapping("/login")
    @Operation(summary = "더미 사용자 로그인", description = "관리자 권한으로 선택한 더미 사용자의 Access 토큰을 발급한다.")
    public ResponseEntity<AdminDummyLoginResponseDto> loginAsDummyUser(
            Authentication authentication,
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody AdminDummyLoginRequestDto request
    ) {
        Long adminUserId = currentUserId(authentication);
        String requestIp = resolveRequestIp(httpServletRequest);
        return ResponseEntity.ok(adminDummyAuthService.loginAsDummyUser(adminUserId, requestIp, request));
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

