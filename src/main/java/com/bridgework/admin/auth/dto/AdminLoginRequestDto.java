package com.bridgework.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 로그인 요청 DTO")
public record AdminLoginRequestDto(
        @Schema(description = "관리자 로그인 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "loginId는 필수입니다.")
        String loginId,
        @Schema(description = "관리자 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "password는 필수입니다.")
        String password
) {
}
