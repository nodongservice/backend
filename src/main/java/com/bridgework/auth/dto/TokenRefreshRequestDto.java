package com.bridgework.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 재발급 요청 DTO")
public record TokenRefreshRequestDto(
        @Schema(description = "리프레시 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken
) {
}
