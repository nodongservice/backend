package com.bridgework.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequestDto(
        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken
) {
}
