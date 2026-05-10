package com.bridgework.profile.dto;

import java.util.List;

public record ProfilePortfolioExtractResponseDto(
        ProfilePortfolioDraftDto draft,
        List<String> missingFields,
        Double confidence,
        Integer ocrTextLength,
        String modelVersion,
        Boolean usedLlm,
        List<String> warnings
) {
}
