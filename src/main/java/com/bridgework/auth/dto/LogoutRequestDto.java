package com.bridgework.auth.dto;

public record LogoutRequestDto(
        String refreshToken
) {
}
