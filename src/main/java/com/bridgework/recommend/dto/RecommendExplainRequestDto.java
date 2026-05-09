package com.bridgework.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "추천 설명 생성 요청 DTO")
public record RecommendExplainRequestDto(
        @Schema(description = "선택 프로필 ID(user_profile.id). 미지정 시 기본 프로필 사용", example = "3")
        Long profileId,
        @NotNull(message = "job은 필수입니다.")
        @Valid
        @Schema(description = "설명 생성 대상 공고")
        RecommendExplainJobDto job,
        @Valid
        @Schema(description = "지도 추천 점수 상세")
        RecommendExplainScoreDetailDto scoreDetail,
        @Schema(description = "총점(지도 추천)", example = "84")
        Integer totalScore,
        @Schema(description = "직무 적합도 점수(퀵 추천)", example = "86")
        Integer jobFitScore,
        @Schema(description = "추천 사유 원문", example = "[\"직무 유사도 높음\", \"요구학력 충족\"]")
        List<String> reasons,
        @Schema(description = "주의사항 원문", example = "[\"보행 이동 부담 가능성\"]")
        List<String> riskFactors,
        @Schema(description = "근거 아이템")
        List<RecommendExplainEvidenceItemDto> evidenceItems
) {
}
