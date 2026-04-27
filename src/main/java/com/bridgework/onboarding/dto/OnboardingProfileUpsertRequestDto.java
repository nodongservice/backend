package com.bridgework.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import java.time.LocalDate;
import java.util.List;

public record OnboardingProfileUpsertRequestDto(
        @NotBlank(message = "희망 직무는 필수입니다.")
        String desiredJob,
        @NotBlank(message = "통근 범위는 필수입니다.")
        String commuteRange,
        @NotEmpty(message = "선호 업무환경은 1개 이상 필요합니다.")
        List<String> preferredWorkEnvironments,
        @NotEmpty(message = "기피 업무환경은 1개 이상 필요합니다.")
        List<String> avoidedWorkEnvironments,
        @NotEmpty(message = "필요 지원사항은 1개 이상 필요합니다.")
        List<String> requiredSupports,
        @NotBlank(message = "장애 종류는 필수입니다.")
        String disabilityType,
        @NotBlank(message = "경력 요약은 필수입니다.")
        String careerSummary,
        @NotBlank(message = "학력 요약은 필수입니다.")
        String educationSummary,
        @NotBlank(message = "고용형태 요약은 필수입니다.")
        String employmentTypeSummary,

        @NotBlank(message = "이름은 필수입니다.")
        String fullName,
        @NotBlank(message = "연락처는 필수입니다.")
        String contactPhone,
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String contactEmail,
        LocalDate birthDate,
        String ageGroup,
        @NotBlank(message = "거주지는 필수입니다.")
        String residenceRegion,
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
        @NotBlank(message = "지원 동기는 필수입니다.")
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
