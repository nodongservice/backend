package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "어학 항목")
public record ProfileLanguageEntryDto(
        @Size(max = 200) String languageName,
        @Size(max = 300) String testName,
        @Size(max = 100) String scoreOrGrade,
        @Size(max = 20) String acquiredYearMonth
) {
}
