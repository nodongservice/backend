package com.bridgework.admin.dummy.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminDummyLoginRequestDto(
        @NotBlank(message = "dummyKey는 필수입니다.")
        String dummyKey
) {
}

