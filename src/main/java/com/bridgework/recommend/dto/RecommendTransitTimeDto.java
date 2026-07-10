package com.bridgework.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "예상 통근시간 정보")
public record RecommendTransitTimeDto(
        @Schema(description = "통근시간 산정 제공자", example = "bridgework")
        String provider,
        @Schema(description = "산정 방식", example = "estimated_transit")
        String mode,
        @Schema(description = "예상 총 소요시간(분)", example = "42")
        Integer durationMinutes,
        @Schema(description = "예상 총 이동거리(미터)", example = "18350.2")
        Double distanceMeters,
        @Schema(description = "예상 도보 거리(미터)", example = "620")
        Integer walkDistanceMeters,
        @Schema(description = "예상 운임", example = "0")
        Integer fare,
        @Schema(description = "예상 환승 횟수", example = "1")
        Integer transferCount,
        @Schema(description = "경로 유형", example = "0")
        Integer pathType,
        @Schema(description = "출발 정류장/역명", example = "강남역")
        String firstStartStation,
        @Schema(description = "도착 정류장/역명", example = "역삼역")
        String lastEndStation,
        @Schema(description = "예상 통근시간 기준 출발 시각", example = "2026-07-13T08:00:00+09:00")
        String requestedDepartureAt,
        @Schema(description = "출발 시각 정책", example = "weekday_08:00_statistical_estimate")
        String departurePolicy,
        @Schema(description = "출처", example = "Bridgework 대중교통 유사 추정")
        String source,
        @Schema(description = "계산 실패 사유", example = "출발지 또는 도착지 좌표가 유효하지 않습니다.")
        String errorReason
) {
}
