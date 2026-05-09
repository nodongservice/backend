package com.bridgework.admin.dummy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 더미 로그인 요청 DTO")
public record AdminDummyLoginRequestDto(
        @Schema(description = "더미 사용자 키", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "dummyKey는 필수입니다.")
        String dummyKey
) {
}
