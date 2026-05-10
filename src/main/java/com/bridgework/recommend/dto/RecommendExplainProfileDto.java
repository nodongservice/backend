package com.bridgework.recommend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "설명 생성에 사용할 프로필 정보")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendExplainProfileDto(
        @JsonAlias({"profile_id"})
        @Schema(description = "프로필 ID", example = "3")
        Long profileId,
        @JsonAlias({"user_id"})
        @Schema(description = "사용자 ID", example = "1001")
        Long userId,
        @Schema(description = "이름", example = "홍길동")
        String name,
        @Schema(description = "주소", example = "서울특별시 강남구")
        String address,
        @JsonAlias({"home_lat"})
        @Schema(description = "자택 위도", example = "37.498095")
        Double homeLat,
        @JsonAlias({"home_lng"})
        @Schema(description = "자택 경도", example = "127.027610")
        Double homeLng,
        @JsonAlias({"desired_jobs"})
        @Schema(description = "희망 직무 목록")
        List<String> desiredJobs,
        @Schema(description = "보유 기술 목록")
        List<String> skills,
        @Schema(description = "학력", example = "대졸")
        String education,
        @Schema(description = "경력", example = "백엔드 개발 3년")
        String career,
        @Schema(description = "전공", example = "컴퓨터공학")
        String major,
        @Schema(description = "자격증 목록")
        List<String> licenses,
        @JsonAlias({"job_fit_statement"})
        @Schema(description = "직무 적합성 설명")
        String jobFitStatement,
        @JsonAlias({"available_employment_types"})
        @Schema(description = "가능한 고용형태")
        List<String> availableEmploymentTypes,
        @JsonAlias({"desired_salary"})
        @Schema(description = "희망 급여", example = "3200")
        Integer desiredSalary,
        @JsonAlias({"time_preference"})
        @Schema(description = "시간 선호", example = "주간")
        String timePreference,
        @JsonAlias({"remote_work"})
        @Schema(description = "재택 가능 여부", example = "true")
        Boolean remoteWork,
        @JsonAlias({"disability_types"})
        @Schema(description = "장애 유형 목록")
        List<String> disabilityTypes,
        @JsonAlias({"disability_severity"})
        @Schema(description = "장애 정도", example = "중등도")
        String disabilitySeverity,
        @JsonAlias({"is_registered_disabled"})
        @Schema(description = "장애인 등록 여부", example = "true")
        Boolean isRegisteredDisabled,
        @JsonAlias({"disability_description"})
        @Schema(description = "상세 장애 설명")
        String disabilityDescription,
        @JsonAlias({"assistive_devices"})
        @Schema(description = "보조기기 목록")
        List<String> assistiveDevices,
        @JsonAlias({"required_supports"})
        @Schema(description = "필요 지원사항 목록")
        List<String> requiredSupports,
        @JsonAlias({"mobility_range_km"})
        @Schema(description = "이동 가능 범위(km)", example = "10.5")
        Double mobilityRangeKm
) {
}
