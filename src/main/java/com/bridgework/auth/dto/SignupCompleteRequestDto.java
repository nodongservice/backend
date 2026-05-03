package com.bridgework.auth.dto;

import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupCompleteRequestDto(
        @NotBlank(message = "signupToken은 필수입니다.")
        String signupToken,
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,
        @Valid
        @NotNull(message = "기본 프로필 입력은 필수입니다.")
        UserProfileUpsertRequestDto profile
) {
}
