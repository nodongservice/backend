package com.bridgework.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 게이트웨이 요청 DTO")
public record RecommendRequestDto(
        @Schema(
                description = "AI 추천 사용 여부. null 또는 true면 FastAPI 연동을 사용하고, false면 Spring DB 조회만 사용한다.",
                example = "true"
        )
        Boolean aiEnabled,
        @Schema(
                description = "선택 프로필 ID(onboarding_profile.id). 미지정 시 기본 프로필을 사용한다.",
                example = "3"
        )
        Long profileId
) {

    public boolean useAi() {
        return aiEnabled == null || aiEnabled;
    }
}

