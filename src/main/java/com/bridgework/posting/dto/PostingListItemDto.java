package com.bridgework.posting.dto;

import java.time.OffsetDateTime;

public record PostingListItemDto(
        Long postingId,
        String externalId,
        String companyName,
        String jobTitle,
        String workAddress,
        String employmentType,
        String salaryType,
        String salary,
        String termDate,
        String registeredAt,
        String postingStatus,
        long scrapCount,
        OffsetDateTime scrappedAt
) {
}
