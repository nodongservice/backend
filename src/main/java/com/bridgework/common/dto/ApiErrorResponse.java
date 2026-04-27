package com.bridgework.common.dto;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        String errorCode,
        String message,
        OffsetDateTime timestamp
) {
}
