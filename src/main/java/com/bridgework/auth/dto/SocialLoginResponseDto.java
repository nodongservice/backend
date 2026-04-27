package com.bridgework.auth.dto;

import com.bridgework.auth.entity.SocialProvider;

public record SocialLoginResponseDto(
        boolean signupRequired,
        String signupToken,
        SocialProvider provider,
        String email,
        String name,
        TokenPairResponseDto tokenPair
) {
}
