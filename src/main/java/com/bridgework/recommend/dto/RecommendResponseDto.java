package com.bridgework.recommend.dto;

import java.util.List;
import java.util.Map;

public record RecommendResponseDto(
        boolean aiEnabled,
        Long profileId,
        List<RecommendJobResponseDto> jobs,
        Map<String, Object> aiResponse
) {

    public static RecommendResponseDto fromAi(Long profileId, Map<String, Object> aiResponse) {
        return new RecommendResponseDto(true, profileId, extractJobs(aiResponse), aiResponse);
    }

    public static RecommendResponseDto fromDb(List<RecommendJobResponseDto> jobs) {
        return new RecommendResponseDto(false, null, jobs, null);
    }

    private static List<RecommendJobResponseDto> extractJobs(Map<String, Object> aiResponse) {
        if (aiResponse == null) {
            return List.of();
        }
        Object resultsValue = aiResponse.get("results");
        if (!(resultsValue instanceof List<?> results)) {
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

