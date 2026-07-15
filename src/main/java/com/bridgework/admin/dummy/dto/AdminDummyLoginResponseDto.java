package com.bridgework.admin.dummy.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminDummyLoginResponseDto(
        String accessToken,
        @JsonIgnore
        String refreshToken,
        String tokenType,
        OffsetDateTime accessTokenExpiresAt,
        OffsetDateTime refreshTokenExpiresAt,
        Long userId,
        String dummyKey,
        List<AdminDummyProfileOptionDto> profiles
) {
}
