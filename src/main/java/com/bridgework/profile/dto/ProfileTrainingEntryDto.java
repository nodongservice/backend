package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "교육 항목")
public record ProfileTrainingEntryDto(
        String trainingType,
        String trainingName,
        String institutionName,
        String startYearMonth,
        String endYearMonth,
        String trainingDescription
) {
}
