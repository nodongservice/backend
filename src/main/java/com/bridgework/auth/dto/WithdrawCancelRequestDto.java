package com.bridgework.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawCancelRequestDto(
        @NotBlank(message = "탈퇴 취소 토큰은 필수입니다.")
        String withdrawalCancelToken
) {
}
