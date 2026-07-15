package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "학력 항목")
public record ProfileEducationEntryDto(
        @Size(max = 80) String schoolType,
        @Size(max = 300) String schoolName,
        @Size(max = 20) String admissionYear,
        @Size(max = 20) String graduationYear,
        @Size(max = 80) String graduationStatus
) {
}
