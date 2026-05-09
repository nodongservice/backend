package com.bridgework.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그아웃 요청 DTO")
public record LogoutRequestDto(
        @Schema(description = "리프레시 토큰(선택, 전달 시 폐기)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String refreshToken
) {
}
