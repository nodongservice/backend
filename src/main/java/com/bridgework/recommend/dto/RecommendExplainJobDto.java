package com.bridgework.recommend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "설명 생성 대상 공고 정보")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendExplainJobDto(
        @JsonAlias({"job_post_id"})
        @Schema(description = "공고 ID(sourceId 대체 허용)", example = "12345")
        Long jobPostId,
        @NotBlank(message = "companyName은 필수입니다.")
        @JsonAlias({"busplaName", "company_name"})
        @Schema(description = "기업명", example = "브릿지웍스")
        String companyName,
        @NotBlank(message = "jobTitle은 필수입니다.")
        @JsonAlias({"jobNm", "job_title"})
        @Schema(description = "직무명", example = "사무보조")
        String jobTitle,
        @JsonAlias({"compAddr", "work_address"})
        @Schema(description = "근무지 주소", example = "서울특별시 강남구 테헤란로 123")
        String workAddress,
        @JsonAlias({"geoLatitude", "work_lat"})
        @Schema(description = "근무지 위도", example = "37.498095")
        Double workLat,
        @JsonAlias({"geoLongitude", "work_lng"})
        @Schema(description = "근무지 경도", example = "127.027610")
        Double workLng,
        @JsonAlias({"empType", "employment_type"})
        @Schema(description = "고용형태", example = "정규직")
        String employmentType,
        @JsonAlias({"enter_type"})
        @Schema(description = "입사형태", example = "신입")
        String enterType,
        @JsonAlias({"salary_type"})
        @Schema(description = "급여형태", example = "월급")
        String salaryType,
        @Schema(description = "급여", example = "3200만원")
        String salary,
        @JsonAlias({"term_date"})
        @Schema(description = "마감일", example = "20261231")
        String termDate,
        @JsonAlias({"reqCareer", "required_career"})
        @Schema(description = "요구경력", example = "무관")
        String requiredCareer,
        @JsonAlias({"reqEduc", "required_education"})
        @Schema(description = "요구학력", example = "고졸")
        String requiredEducation,
        @JsonAlias({"reqMajor", "required_major"})
        @Schema(description = "요구전공", example = "무관")
        String requiredMajor,
        @JsonAlias({"reqLicens", "required_licenses"})
        @Schema(description = "요구자격증", example = "컴퓨터활용능력")
        String requiredLicenses,
        @JsonAlias({"regagnName", "agency_name"})
        @Schema(description = "담당기관", example = "서울강남고용센터")
        String agencyName,
        @JsonAlias({"regDt", "registered_at"})
        @Schema(description = "공고 등록일시", example = "20260508")
        String registeredAt,
        @JsonAlias({"source_table"})
        @Schema(description = "원천 테이블명", example = "pd_kepad_recruitment")
        String sourceTable,
        @JsonAlias({"source_id"})
        @Schema(description = "원천 레코드 ID", example = "98765")
        Long sourceId
) {
}
