package com.bridgework.recommend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "설명 생성 대상 공고 정보")
public record RecommendExplainJobDto(
        @NotNull(message = "jobPostId는 필수입니다.")
        @Schema(description = "공고 ID", example = "12345")
        Long jobPostId,
        @NotBlank(message = "companyName은 필수입니다.")
        @Schema(description = "기업명", example = "브릿지웍스")
        String companyName,
        @NotBlank(message = "jobTitle은 필수입니다.")
        @Schema(description = "직무명", example = "사무보조")
        String jobTitle,
        @Schema(description = "근무지 주소", example = "서울특별시 강남구 테헤란로 123")
        String workAddress,
        @Schema(description = "근무지 위도", example = "37.498095")
        Double workLat,
        @Schema(description = "근무지 경도", example = "127.027610")
        Double workLng,
        @Schema(description = "고용형태", example = "정규직")
        String employmentType,
        @Schema(description = "입사형태", example = "신입")
        String enterType,
        @Schema(description = "급여형태", example = "월급")
        String salaryType,
        @Schema(description = "급여", example = "3200만원")
        String salary,
        @Schema(description = "마감일", example = "20261231")
        String termDate,
        @Schema(description = "요구경력", example = "무관")
        String requiredCareer,
        @Schema(description = "요구학력", example = "고졸")
        String requiredEducation,
        @Schema(description = "요구전공", example = "무관")
        String requiredMajor,
        @Schema(description = "요구자격증", example = "컴퓨터활용능력")
        String requiredLicenses,
        @Schema(description = "담당기관", example = "서울강남고용센터")
        String agencyName,
        @Schema(description = "공고 등록일시", example = "20260508")
        String registeredAt,
        @Schema(description = "원천 테이블명", example = "pd_kepad_recruitment")
        String sourceTable,
        @Schema(description = "원천 레코드 ID", example = "98765")
        Long sourceId,
        @Schema(description = "외부 공고 식별자", example = "KEPAD-20260508-0001")
        String externalId
) {
}
