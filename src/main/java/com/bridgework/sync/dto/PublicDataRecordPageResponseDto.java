package com.bridgework.sync.dto;

import java.util.List;

public record PublicDataRecordPageResponseDto(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<PublicDataRecordResponseDto> records
) {
}
