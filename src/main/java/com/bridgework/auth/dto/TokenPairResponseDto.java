package com.bridgework.auth.dto;

import java.time.OffsetDateTime;

public record TokenPairResponseDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        OffsetDateTime accessTokenExpiresAt,
        OffsetDateTime refreshTokenExpiresAt
) {
}
