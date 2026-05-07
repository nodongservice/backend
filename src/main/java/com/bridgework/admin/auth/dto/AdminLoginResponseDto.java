package com.bridgework.admin.auth.dto;

import java.time.OffsetDateTime;

public record AdminLoginResponseDto(
        String accessToken,
        String tokenType,
        OffsetDateTime accessTokenExpiresAt
) {
}
