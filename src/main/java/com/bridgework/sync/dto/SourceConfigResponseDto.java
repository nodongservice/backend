package com.bridgework.sync.dto;

import com.bridgework.sync.entity.PublicDataSourceType;

public record SourceConfigResponseDto(
        PublicDataSourceType sourceType,
        boolean enabled,
        String baseUrl,
        int pageSize,
        int maxPages
) {
}
