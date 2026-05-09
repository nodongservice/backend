package com.bridgework.profile.dto;

import com.bridgework.auth.entity.GenderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import java.time.LocalDate;
import java.util.List;

@Schema(
        description = "회원 프로필 생성/수정 요청 DTO",
        requiredProperties = {
                "disabilityType",
                "fullName",
                "contactPhone",
                "contactEmail",
                "birthDate",
                "genderType",
                "residenceRegion",
                "detailAddress",
                "highestEducation",
                "graduationStatus",
                "majorCareer",
                "targetJob",
                "skills",
                "disabilityYn",
                "disabilitySeverity",
                "disabilityRegisteredYn",
                "workAvailability",
                "workTypes",
                "selfIntroduction"
        }
)
public record UserProfileUpsertRequestDto(
        // 기능 2/3 화면 필터에서 주로 사용하는 값으로 선택 입력을 허용한다.
        String desiredJob,
        // 통근 범위는 화면 필터에서 매 요청마다 선택 가능하므로 선택 입력으로 둔다.
        String commuteRange,
        List<String> preferredWorkEnvironments,
        List<String> avoidedWorkEnvironments,
        List<String> requiredSupports,
        @NotBlank(message = "장애 종류는 필수입니다.")
        String disabilityType,
        String careerSummary,
        String educationSummary,
        String employmentTypeSummary,

        @NotBlank(message = "이름은 필수입니다.")
        String fullName,
        @NotBlank(message = "연락처는 필수입니다.")
        String contactPhone,
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String contactEmail,
        @NotNull(message = "생년월일은 필수입니다.")
        LocalDate birthDate,
        @NotNull(message = "성별은 필수입니다.")
        GenderType genderType,
        String ageGroup,
        @NotBlank(message = "거주 지역은 필수입니다.")
        String residenceRegion,
        @NotBlank(message = "상세 주소는 필수입니다.")
        String detailAddress,
        String emergencyContact,
        String profileImageUrl,

        @NotBlank(message = "최종 학력은 필수입니다.")
        String highestEducation,
        @NotBlank(message = "졸업 여부는 필수입니다.")
        String graduationStatus,
        @NotBlank(message = "주요 경력은 필수입니다.")
        String majorCareer,
        String careerDetail,
        String projectExperience,
        String careerGapReason,

        @NotBlank(message = "지원 직무는 필수입니다.")
        String targetJob,
        @NotEmpty(message = "보유 기술/역량은 1개 이상 필요합니다.")
        List<String> skills,
        List<String> certifications,
        String portfolioUrl,
        String awards,
        String trainings,

        @NotNull(message = "장애 여부는 필수입니다.")
        Boolean disabilityYn,
        @NotBlank(message = "장애 정도는 필수입니다.")
        String disabilitySeverity,
        @NotNull(message = "장애인 등록 여부는 필수입니다.")
        Boolean disabilityRegisteredYn,
        String disabilityDescription,
        String assistiveDevices,
        String workSupportRequirements,

        @NotBlank(message = "근무 가능 여부는 필수입니다.")
        String workAvailability,
        @NotEmpty(message = "근무 형태는 1개 이상 필요합니다.")
        List<String> workTypes,
        String expectedSalary,
        String workTimePreference,
        Boolean remoteAvailableYn,
        String mobilityRange,

        @NotBlank(message = "자기소개는 필수입니다.")
        String selfIntroduction,
        String motivation,
        String jobFitDescription,
        String careerGoal,
        String strengthsWeaknesses,

        String militaryService,
        Boolean patrioticVeteranYn,
        String referrer,
        String snsUrl
) {
}
