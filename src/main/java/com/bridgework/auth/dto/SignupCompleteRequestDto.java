package com.bridgework.auth.dto;

import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "소셜 회원가입 완료 요청 DTO")
public record SignupCompleteRequestDto(
        @Schema(description = "최초 회원가입 세션 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "signupToken은 필수입니다.")
        String signupToken,
        @Schema(description = "회원 이메일(선택 덮어쓰기)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,
        @Schema(description = "회원 기본 프로필", requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid
        @NotNull(message = "기본 프로필 입력은 필수입니다.")
        UserProfileUpsertRequestDto profile
) {
}
