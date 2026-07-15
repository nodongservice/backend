package com.bridgework.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "토큰 재발급 요청 DTO")
public record TokenRefreshRequestDto(
        @Schema(description = "레거시 클라이언트용 리프레시 토큰. 브라우저는 HttpOnly 쿠키를 사용합니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String refreshToken
) {
}
