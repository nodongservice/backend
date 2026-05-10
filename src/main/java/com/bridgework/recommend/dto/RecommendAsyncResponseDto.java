package com.bridgework.recommend.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record RecommendAsyncResponseDto(
        String requestId,
        String requestType,
        RecommendTaskStatus status,
        Map<String, Object> result,
        String errorMessage,
        OffsetDateTime expiresAt,
        OffsetDateTime updatedAt
) {
}
