package com.bridgework.admin.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;

public record AdminLoginResponseDto(
        String accessToken,
        @JsonIgnore
        String refreshToken,
        String tokenType,
        OffsetDateTime accessTokenExpiresAt,
        OffsetDateTime refreshTokenExpiresAt
) {
}
