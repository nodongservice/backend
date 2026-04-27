package com.bridgework.auth.dto;

import com.bridgework.auth.entity.GenderType;
import com.bridgework.auth.entity.SocialProvider;
import com.bridgework.auth.entity.UserRole;

public record AuthMeResponseDto(
        Long userId,
        SocialProvider provider,
        String email,
        String name,
        Integer age,
        GenderType gender,
        String location,
        String phoneNumber,
        UserRole role,
        boolean signupCompleted
) {
}
