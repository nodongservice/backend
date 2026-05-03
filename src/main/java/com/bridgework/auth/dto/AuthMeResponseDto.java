package com.bridgework.auth.dto;

import com.bridgework.auth.entity.SocialProvider;
import com.bridgework.auth.entity.UserRole;

public record AuthMeResponseDto(
        Long userId,
        SocialProvider provider,
        String email,
        UserRole role,
        boolean signupCompleted
) {
}
