package com.bridgework.profile.dto;

import com.bridgework.auth.entity.GenderType;
import com.bridgework.profile.enums.ProfileDisabilitySeverity;
import com.bridgework.profile.enums.ProfileDisabilityType;
import com.bridgework.profile.enums.ProfileGraduationStatus;
import com.bridgework.profile.enums.ProfileHighestEducation;
import com.bridgework.profile.enums.ProfileMilitaryService;
import com.bridgework.profile.enums.ProfileWorkAvailability;
import com.bridgework.profile.enums.ProfileWorkTimePreference;
import com.bridgework.profile.enums.ProfileWorkType;
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
                "detailAddress",
                "highestEducation",
                "graduationStatus",
                "majorCareer",
                "targetJob",
                "skills",
                "disabilitySeverity",
                "disabilityRegisteredYn",
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
        @NotNull(message = "장애 종류는 필수입니다.")
        ProfileDisabilityType disabilityType,
        String careerSummary,
        String educationSummary,
        String employmentTypeSummary,
        String profileName,

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
        @NotBlank(message = "상세 주소는 필수입니다.")
        String detailAddress,
        String emergencyContact,

        @NotNull(message = "최종 학력은 필수입니다.")
        ProfileHighestEducation highestEducation,
        @NotNull(message = "졸업 여부는 필수입니다.")
        ProfileGraduationStatus graduationStatus,
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

        @NotNull(message = "장애 정도는 필수입니다.")
        ProfileDisabilitySeverity disabilitySeverity,
        @NotNull(message = "장애인 등록 여부는 필수입니다.")
        Boolean disabilityRegisteredYn,
        String disabilityDescription,
        String assistiveDevices,
        String workSupportRequirements,

        ProfileWorkAvailability workAvailability,
        @NotEmpty(message = "근무 형태는 1개 이상 필요합니다.")
        List<ProfileWorkType> workTypes,
        String expectedSalary,
        ProfileWorkTimePreference workTimePreference,
        Boolean remoteAvailableYn,
        String mobilityRange,

        @NotBlank(message = "자기소개는 필수입니다.")
        String selfIntroduction,
        String motivation,
        String jobFitDescription,
        String careerGoal,
        String strengthsWeaknesses,

        ProfileMilitaryService militaryService,
        Boolean patrioticVeteranYn,
        String referrer,
        String snsUrl
) {
}
