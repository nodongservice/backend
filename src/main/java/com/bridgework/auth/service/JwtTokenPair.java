package com.bridgework.auth.service;

import java.time.OffsetDateTime;

public record JwtTokenPair(
        String accessToken,
        String refreshToken,
        String refreshTokenId,
        String sessionId,
        OffsetDateTime accessTokenExpiresAt,
        OffsetDateTime refreshTokenExpiresAt
) {
}
