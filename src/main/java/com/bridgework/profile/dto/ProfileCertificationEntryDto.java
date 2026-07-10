package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자격증 항목")
public record ProfileCertificationEntryDto(
        String issuer,
        String certificationName,
        String acquiredYearMonth
) {
}
