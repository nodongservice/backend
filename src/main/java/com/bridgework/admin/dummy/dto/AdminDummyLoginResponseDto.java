package com.bridgework.admin.dummy.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminDummyLoginResponseDto(
        String accessToken,
        String tokenType,
        OffsetDateTime accessTokenExpiresAt,
        Long userId,
        String dummyKey,
        List<AdminDummyProfileOptionDto> profiles
) {
}

