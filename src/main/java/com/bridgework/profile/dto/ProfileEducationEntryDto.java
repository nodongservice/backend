package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학력 항목")
public record ProfileEducationEntryDto(
        String schoolType,
        String schoolName,
        String admissionYear,
        String graduationYear,
        String graduationStatus
) {
}
