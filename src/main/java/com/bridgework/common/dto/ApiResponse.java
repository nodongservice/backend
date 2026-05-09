package com.bridgework.common.dto;

public record ApiResponse<T>(
        String code,
        String message,
        T result
) {

    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>("SUCCESS", "요청이 성공했습니다.", result);
    }

    public static <T> ApiResponse<T> success(String code, String message, T result) {
        return new ApiResponse<>(code, message, result);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}

