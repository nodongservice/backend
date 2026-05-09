package com.bridgework.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 공고 응답 항목 DTO")
public record RecommendJobResponseDto(
        @Schema(description = "외부 공고 식별자", example = "KEPAD-20260508-0001")
        String externalId,
        @Schema(description = "사업장명", example = "브릿지웍스")
        String busplaName,
        @Schema(description = "직무명", example = "사무보조")
        String jobNm,
        @Schema(description = "근무지 주소", example = "서울특별시 강남구 테헤란로 123")
        String compAddr,
        @Schema(description = "고용형태", example = "정규직")
        String empType,
        @Schema(description = "입사형태", example = "신입")
        String enterType,
        @Schema(description = "급여형태", example = "월급")
        String salaryType,
        @Schema(description = "급여", example = "3200만원")
        String salary,
        @Schema(description = "마감일", example = "20261231")
        String termDate,
        @Schema(description = "구인신청일", example = "20260508")
        String offerregDt,
        @Schema(description = "등록일", example = "20260508")
        String regDt,
        @Schema(description = "요구경력", example = "무관")
        String reqCareer,
        @Schema(description = "요구학력", example = "고졸")
        String reqEduc,
        @Schema(description = "요구전공", example = "무관")
        String reqMajor,
        @Schema(description = "요구자격증", example = "컴퓨터활용능력")
        String reqLicens,
        @Schema(description = "위도", example = "37.498095")
        Double geoLatitude,
        @Schema(description = "경도", example = "127.027610")
        Double geoLongitude
) {
}

