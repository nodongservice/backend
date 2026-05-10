package com.bridgework.recommend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지도 추천 종합 점수 상세")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendExplainScoreDetailDto(
        @JsonAlias({"job_fit_score"})
        @Schema(description = "직무 적합도", example = "86")
        Integer jobFitScore,
        @JsonAlias({"work_condition_score"})
        @Schema(description = "근무조건 적합도", example = "80")
        Integer workConditionScore,
        @JsonAlias({"disability_support_score"})
        @Schema(description = "장애 지원 적합도", example = "82")
        Integer disabilitySupportScore,
        @JsonAlias({"work_environment_score"})
        @Schema(description = "업무환경 적합도", example = "85")
        Integer workEnvironmentScore,
        @JsonAlias({"company_stability_score"})
        @Schema(description = "기업 안정성/채용친화도", example = "83")
        Integer companyStabilityScore,
        @JsonAlias({"accessibility_score"})
        @Schema(description = "접근성 요약 점수", example = "88")
        Integer accessibilityScore
) {
}
