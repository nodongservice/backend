package com.bridgework.sync.dto;

import java.time.OffsetDateTime;

public record ErrorResponseDto(
        String errorCode,
        String message,
        OffsetDateTime timestamp
) {
}
