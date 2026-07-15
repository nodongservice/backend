package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "포트폴리오 또는 URL 항목")
public record ProfilePortfolioEntryDto(
        @Size(max = 80) String portfolioType,
        @Size(max = 300) String title,
        @Size(max = 1000) String url
) {
}
