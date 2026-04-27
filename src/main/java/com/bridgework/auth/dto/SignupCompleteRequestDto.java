package com.bridgework.auth.dto;

import com.bridgework.auth.entity.GenderType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupCompleteRequestDto(
        @NotBlank(message = "signupToken은 필수입니다.")
        String signupToken,
        @NotBlank(message = "이름은 필수입니다.")
        String name,
        @NotNull(message = "나이는 필수입니다.")
        @Min(value = 14, message = "나이는 14 이상이어야 합니다.")
        @Max(value = 120, message = "나이는 120 이하여야 합니다.")
        Integer age,
        @NotNull(message = "성별은 필수입니다.")
        GenderType gender,
        @NotBlank(message = "위치정보는 필수입니다.")
        String location,
        @NotBlank(message = "전화번호는 필수입니다.")
        String phoneNumber,
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {
}
