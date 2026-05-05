package com.bridgework.sync.dto;

import com.bridgework.sync.entity.PublicDataSourceType;
import java.time.OffsetDateTime;

public record SyncRunAcceptedResponseDto(
        OffsetDateTime requestedAt,
        PublicDataSourceType sourceType,
        String message
) {
}
