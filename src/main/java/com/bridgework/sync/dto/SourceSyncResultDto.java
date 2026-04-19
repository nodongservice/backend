package com.bridgework.sync.dto;

import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.SyncStatus;

public record SourceSyncResultDto(
        PublicDataSourceType sourceType,
        SyncStatus status,
        int processedCount,
        int newCount,
        int updatedCount,
        int failedCount,
        String message
) {
}
