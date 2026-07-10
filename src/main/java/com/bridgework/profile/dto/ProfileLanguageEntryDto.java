package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "어학 항목")
public record ProfileLanguageEntryDto(
        String languageName,
        String testName,
        String scoreOrGrade,
        String acquiredYearMonth
) {
}
