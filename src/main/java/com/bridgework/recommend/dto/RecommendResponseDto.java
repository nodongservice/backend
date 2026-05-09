package com.bridgework.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "추천 게이트웨이 응답 DTO")
public record RecommendResponseDto(
        @Schema(description = "AI 추천 사용 여부", example = "true")
        boolean aiEnabled,
        @Schema(description = "선택 프로필 ID(user_profile.id)", example = "3")
        Long profileId,
        @Schema(description = "추천 공고 목록")
        List<RecommendJobResponseDto> jobs,
        @Schema(
                description = "FastAPI 원본 응답. aiEnabled=true일 때만 포함된다.",
                example = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"results\":[{\"job\":{\"external_id\":\"KEPAD-20260508-0001\",\"company_name\":\"브릿지웍스\",\"job_title\":\"사무보조\"},\"total_score\":85,\"score_detail\":{\"job_fit_score\":90}}]}}"
        )
        Map<String, Object> aiResponse
) {

    public static RecommendResponseDto fromAi(Long profileId, Map<String, Object> aiResponse) {
        return new RecommendResponseDto(true, profileId, extractJobs(aiResponse), aiResponse);
    }

    public static RecommendResponseDto fromDb(List<RecommendJobResponseDto> jobs) {
        return new RecommendResponseDto(false, null, jobs, null);
    }

    private static List<RecommendJobResponseDto> extractJobs(Map<String, Object> aiResponse) {
        List<?> results = resolveResults(aiResponse);
        if (results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(result -> result.get("job"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(RecommendResponseDto::toRecommendJob)
                .toList();
    }

    private static List<?> resolveResults(Map<String, Object> aiResponse) {
        if (aiResponse == null) {
            return List.of();
        }

        // 현재 FastAPI 표준 응답: { "code": "...", "message": "...", "result": { "results": [...] } }
        Object resultValue = aiResponse.get("result");
        if (resultValue instanceof Map<?, ?> resultMap) {
            Object nestedResults = resultMap.get("results");
            if (nestedResults instanceof List<?> nestedList) {
                return nestedList;
            }
        }

        // 하위 호환: 기존 포맷 { "results": [...] }도 허용
        Object resultsValue = aiResponse.get("results");
        if (resultsValue instanceof List<?> results) {
            return results;
        }

        return List.of();
    }

    private static RecommendJobResponseDto toRecommendJob(Map<?, ?> job) {
        String registeredAt = asString(job.get("registered_at"));
        return new RecommendJobResponseDto(
                asString(job.get("external_id")),
                asString(job.get("company_name")),
                asString(job.get("job_title")),
                asString(job.get("work_address")),
                asString(job.get("employment_type")),
                asString(job.get("enter_type")),
                asString(job.get("salary_type")),
                asString(job.get("salary")),
                asString(job.get("term_date")),
                registeredAt,
                registeredAt,
                asString(job.get("required_career")),
                asString(job.get("required_education")),
                asString(job.get("required_major")),
                asString(job.get("required_licenses")),
                asDouble(job.get("work_lat")),
                asDouble(job.get("work_lng"))
        );
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
