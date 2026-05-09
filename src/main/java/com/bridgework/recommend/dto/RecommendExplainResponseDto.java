package com.bridgework.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "추천 설명 생성 응답 DTO")
public record RecommendExplainResponseDto(
        @Schema(description = "선택 프로필 ID(user_profile.id)", example = "3")
        Long profileId,
        @Schema(description = "요약 문장", example = "직무 적합도와 접근성이 균형 잡힌 공고입니다.")
        String shortSummary,
        @Schema(description = "추천 사유", example = "[\"지원 직무와 요구 역량이 유사합니다.\"]")
        List<String> recommendationReasons,
        @Schema(description = "주의사항", example = "[\"출퇴근 시간대 혼잡 가능성을 확인하세요.\"]")
        List<String> cautionPoints,
        @Schema(description = "체크리스트", example = "[\"면접 전 근무지 접근 경로를 확인하세요.\"]")
        List<String> checklist,
        @Schema(description = "LLM 사용 여부", example = "false")
        Boolean usedLlm,
        @Schema(
                description = "FastAPI 원본 응답",
                example = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"short_summary\":\"...\",\"recommendation_reasons\":[\"...\"],\"caution_points\":[\"...\"],\"checklist\":[\"...\"],\"used_llm\":false}}"
        )
        Map<String, Object> aiResponse
) {
}
