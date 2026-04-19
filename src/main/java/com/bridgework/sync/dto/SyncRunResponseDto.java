package com.bridgework.sync.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SyncRunResponseDto(
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        List<SourceSyncResultDto> results,
        int processedCount,
        int newCount,
        int updatedCount,
        int failedCount
) {
}
