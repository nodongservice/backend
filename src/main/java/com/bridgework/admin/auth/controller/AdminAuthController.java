package com.bridgework.admin.auth.controller;

import com.bridgework.admin.auth.dto.AdminLoginRequestDto;
import com.bridgework.admin.auth.dto.AdminLoginResponseDto;
import com.bridgework.admin.auth.service.AdminAuthService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/admin")
@Tag(name = "AdminAuth", description = "관리자 인증 API")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    @Operation(summary = "관리자 로그인", description = "관리자 계정(loginId/password)으로 로그인해 관리자 전용 Access 토큰을 발급한다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = @ExampleObject(value = "{\"loginId\":\"admin01\",\"password\":\"admin-password\"}"))
    )
    @ApiResponse(responseCode = "200", description = "로그인 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"accessToken\":\"<ADMIN_ACCESS_TOKEN>\",\"tokenType\":\"Bearer\",\"accessTokenExpiresAt\":\"2026-05-08T08:00:00Z\"}"
            )))
    public ResponseEntity<AdminLoginResponseDto> login(@Valid @RequestBody AdminLoginRequestDto request) {
        return ResponseEntity.ok(adminAuthService.login(request));
    }
}
