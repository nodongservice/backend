package com.bridgework.auth.dto;

import com.bridgework.auth.entity.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialLoginRequestDto(
        @NotNull(message = "provider는 필수입니다.")
        SocialProvider provider,
        @NotBlank(message = "code는 필수입니다.")
        String code,
        String redirectUri,
        String state
) {
}
