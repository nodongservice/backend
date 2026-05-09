package com.bridgework.auth.dto;

import com.bridgework.auth.entity.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "소셜 로그인 요청 DTO")
public record SocialLoginRequestDto(
        @Schema(description = "소셜 제공자", example = "KAKAO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "provider는 필수입니다.")
        SocialProvider provider,
        @Schema(description = "인가 코드", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "code는 필수입니다.")
        String code,
        @Schema(description = "리다이렉트 URI", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String redirectUri,
        @Schema(description = "OAuth state", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String state
) {
}
