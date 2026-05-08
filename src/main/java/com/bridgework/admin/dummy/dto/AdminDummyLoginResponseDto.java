package com.bridgework.admin.dummy.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminDummyLoginResponseDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        OffsetDateTime accessTokenExpiresAt,
        OffsetDateTime refreshTokenExpiresAt,
        Long userId,
        String dummyKey,
        List<AdminDummyProfileOptionDto> profiles
) {
}
