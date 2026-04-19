package com.bridgework.sync.dto;

import com.bridgework.sync.entity.PublicDataSourceType;
import java.time.OffsetDateTime;
import java.util.Map;

public record PublicDataRecordResponseDto(
        Long id,
        PublicDataSourceType sourceType,
        String externalId,
        OffsetDateTime rawFetchedAt,
        OffsetDateTime updatedAt,
        Map<String, String> fields,
        String payloadJson
) {
}
