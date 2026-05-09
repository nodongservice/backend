package com.bridgework.auth.dto;

import com.bridgework.auth.entity.SocialProvider;
import java.time.OffsetDateTime;

public record SocialLoginResponseDto(
        boolean signupRequired,
        String signupToken,
        SocialProvider provider,
        String email,
        String name,
        SocialLoginAccountStatus accountStatus,
        OffsetDateTime withdrawalDeadlineAt,
        String withdrawalCancelToken,
        TokenPairResponseDto tokenPair
) {
}
