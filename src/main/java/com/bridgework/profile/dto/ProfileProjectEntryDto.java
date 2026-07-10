package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트 또는 대외활동 항목")
public record ProfileProjectEntryDto(
        String projectType,
        String projectName,
        String startYearMonth,
        String endYearMonth,
        String projectDescription
) {
}
