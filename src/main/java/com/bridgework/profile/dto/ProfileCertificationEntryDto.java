package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "자격증 항목")
public record ProfileCertificationEntryDto(
        @Size(max = 300) String issuer,
        @Size(max = 300) String certificationName,
        @Size(max = 20) String acquiredYearMonth
) {
}
