package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "포트폴리오 또는 URL 항목")
public record ProfilePortfolioEntryDto(
        String portfolioType,
        String title,
        String url
) {
}
