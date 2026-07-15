package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "프로젝트 또는 대외활동 항목")
public record ProfileProjectEntryDto(
        @Size(max = 80) String projectType,
        @Size(max = 300) String projectName,
        @Size(max = 20) String startYearMonth,
        @Size(max = 20) String endYearMonth,
        @Size(max = 3000) String projectDescription
) {
}
