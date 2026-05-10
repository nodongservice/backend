package com.bridgework.recommend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "quick/map 결과에서 선택한 단일 추천 항목")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendExplainSelectedResultDto(
        @NotNull(message = "selectedResult.job은 필수입니다.")
        @Valid
        @Schema(description = "설명 생성 대상 공고")
        RecommendExplainJobDto job,
        @JsonAlias({"score_detail"})
        @Valid
        @Schema(description = "지도 추천 점수 상세")
        RecommendExplainScoreDetailDto scoreDetail,
        @JsonAlias({"total_score"})
        @Schema(description = "총점(지도 추천)", example = "84")
        Integer totalScore,
        @JsonAlias({"job_fit_score"})
        @Schema(description = "직무 적합도 점수(퀵 추천)", example = "86")
        Integer jobFitScore,
        @Schema(description = "추천 사유 원문")
        List<String> reasons,
        @JsonAlias({"risk_factors"})
        @Schema(description = "주의사항 원문")
        List<String> riskFactors,
        @JsonAlias({"evidence_items"})
        @Schema(description = "근거 아이템")
        List<RecommendExplainEvidenceItemDto> evidenceItems
) {
}
