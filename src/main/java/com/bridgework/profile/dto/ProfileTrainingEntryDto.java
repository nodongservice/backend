package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "교육 항목")
public record ProfileTrainingEntryDto(
        @Size(max = 80) String trainingType,
        @Size(max = 300) String trainingName,
        @Size(max = 300) String institutionName,
        @Size(max = 20) String startYearMonth,
        @Size(max = 20) String endYearMonth,
        @Size(max = 3000) String trainingDescription
) {
}
