package com.bridgework.recommend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "추천 설명 생성 요청 DTO")
public record RecommendExplainRequestDto(
        @NotNull(message = "profile은 필수입니다.")
        @Valid
        @Schema(description = "설명 생성에 사용할 프로필")
        RecommendExplainProfileDto profile,
        @Valid
        @Schema(description = "설명 생성 대상 공고. selectedResult를 사용하면 생략 가능")
        RecommendExplainJobDto job,
        @Valid
        @JsonAlias({"selected_result"})
        @Schema(description = "quick/map 결과에서 선택한 단일 추천 항목")
        RecommendExplainSelectedResultDto selectedResult,
        @Valid
        @JsonAlias({"score_detail"})
        @Schema(description = "지도 추천 점수 상세. selectedResult 사용 시 생략 가능")
        RecommendExplainScoreDetailDto scoreDetail,
        @JsonAlias({"total_score"})
        @Schema(description = "총점(지도 추천). selectedResult 사용 시 생략 가능", example = "84")
        Integer totalScore,
        @JsonAlias({"job_fit_score"})
        @Schema(description = "직무 적합도 점수(퀵 추천). selectedResult 사용 시 생략 가능", example = "86")
        Integer jobFitScore,
        @Schema(description = "추천 사유 원문", example = "[\"직무 유사도 높음\", \"요구학력 충족\"]")
        List<String> reasons,
        @JsonAlias({"risk_factors"})
        @Schema(description = "주의사항 원문", example = "[\"보행 이동 부담 가능성\"]")
        List<String> riskFactors,
        @JsonAlias({"evidence_items"})
        @Schema(description = "근거 아이템")
        List<RecommendExplainEvidenceItemDto> evidenceItems
) {
}
