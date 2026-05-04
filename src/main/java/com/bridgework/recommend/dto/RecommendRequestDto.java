package com.bridgework.recommend.dto;

public record RecommendRequestDto(
        Boolean aiEnabled,
        Long profileId
) {

    public boolean useAi() {
        return aiEnabled == null || aiEnabled;
    }
}

