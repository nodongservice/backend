package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "경력 항목")
public record ProfileCareerEntryDto(
        String companyName,
        String departmentName,
        String startYearMonth,
        String endYearMonth,
        String responsibilities
) {
}
