package com.bridgework.sync.dto;

import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.SyncRequestSource;
import com.bridgework.sync.entity.SyncStatus;
import java.time.OffsetDateTime;

public record SyncLogResponseDto(
        Long id,
        PublicDataSourceType sourceType,
        SyncRequestSource requestSource,
        SyncStatus status,
        int processedCount,
        int newCount,
        int updatedCount,
        int failedCount,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
) {
}
