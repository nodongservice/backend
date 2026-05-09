package com.bridgework.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지도 추천 종합 점수 상세")
public record RecommendExplainScoreDetailDto(
        @Schema(description = "직무 적합도", example = "86")
        Integer jobFitScore,
        @Schema(description = "근무조건 적합도", example = "80")
        Integer workConditionScore,
        @Schema(description = "장애 지원 적합도", example = "82")
        Integer disabilitySupportScore,
        @Schema(description = "업무환경 적합도", example = "85")
        Integer workEnvironmentScore,
        @Schema(description = "기업 안정성/채용친화도", example = "83")
        Integer companyStabilityScore,
        @Schema(description = "접근성 요약 점수", example = "88")
        Integer accessibilityScore
) {
}
