package com.bridgework.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "설명 생성에 전달할 근거 아이템")
public record RecommendExplainEvidenceItemDto(
        @Schema(description = "근거 소스 타입", example = "BUS_STOP")
        String sourceType,
        @Schema(description = "근거 소스명", example = "역삼역 버스정류장")
        String sourceName,
        @Schema(description = "근거 설명", example = "근무지에서 320m 거리의 버스정류장")
        String description,
        @Schema(description = "거리(미터)", example = "320.5")
        Double distanceMeters,
        @Schema(description = "원천 테이블명", example = "pd_nationwide_bus_stop")
        String sourceTable,
        @Schema(description = "원천 레코드 ID", example = "123")
        Long recordId,
        @Schema(description = "추가 필드")
        Map<String, Object> fields
) {
}
