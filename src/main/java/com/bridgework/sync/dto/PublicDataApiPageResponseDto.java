package com.bridgework.sync.dto;

import java.util.List;

public record PublicDataApiPageResponseDto(
        List<PublicDataApiItemDto> items,
        boolean hasNext
) {
}
