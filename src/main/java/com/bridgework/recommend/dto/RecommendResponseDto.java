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
        return new RecommendResponseDto(true, profileId, List.of(), aiResponse);
    }

    public static RecommendResponseDto fromDb(List<RecommendJobResponseDto> jobs) {
        return new RecommendResponseDto(false, null, jobs, null);
    }
}

