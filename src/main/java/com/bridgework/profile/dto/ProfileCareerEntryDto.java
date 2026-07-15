package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "경력 항목")
public record ProfileCareerEntryDto(
        @Size(max = 300) String companyName,
        @Size(max = 300) String departmentName,
        @Size(max = 20) String startYearMonth,
        @Size(max = 20) String endYearMonth,
        @Size(max = 3000) String responsibilities
) {
}
