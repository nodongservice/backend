package com.bridgework.sync.dto;

import java.util.List;

public record NormalizedCountSummaryResponseDto(
        long totalCount,
        List<NormalizedSourceCountResponseDto> sourceCounts
) {
}
