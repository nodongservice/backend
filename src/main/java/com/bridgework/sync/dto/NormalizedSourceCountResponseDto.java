package com.bridgework.sync.dto;

import com.bridgework.sync.entity.PublicDataSourceType;

public record NormalizedSourceCountResponseDto(
        PublicDataSourceType sourceType,
        String tableName,
        long rowCount
) {
}
