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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(
        description = "회원 프로필 생성/수정 요청 DTO",
        requiredProperties = {
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
                "sensitiveInfoConsentYn",
                "workTypes",
                "selfIntroduction"
        }
)
public record UserProfileUpsertRequestDto(
        // 기능 2/3 화면 필터에서 주로 사용하는 값으로 선택 입력을 허용한다.
        @Size(max = 200, message = "희망 직무는 200자 이하여야 합니다.")
        String desiredJob,
        // 통근 범위는 화면 필터에서 매 요청마다 선택 가능하므로 선택 입력으로 둔다.
        @Size(max = 120, message = "통근 범위는 120자 이하여야 합니다.")
        String commuteRange,
        List<String> preferredWorkEnvironments,
        List<String> avoidedWorkEnvironments,
        List<String> requiredSupports,
        ProfileDisabilityType disabilityType,
        @Size(max = 500, message = "경력 요약은 500자 이하여야 합니다.")
        String careerSummary,
        @Size(max = 500, message = "학력 요약은 500자 이하여야 합니다.")
        String educationSummary,
        @Size(max = 200, message = "고용 형태 요약은 200자 이하여야 합니다.")
        String employmentTypeSummary,
        @Size(max = 100, message = "프로필 이름은 100자 이하여야 합니다.")
        String profileName,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String fullName,
        @NotBlank(message = "연락처는 필수입니다.")
        @Size(max = 32, message = "연락처는 32자 이하여야 합니다.")
        String contactPhone,
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String contactEmail,
        @NotNull(message = "생년월일은 필수입니다.")
        @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다.")
        LocalDate birthDate,
        @NotNull(message = "성별은 필수입니다.")
        GenderType genderType,
        @Size(max = 50, message = "연령대는 50자 이하여야 합니다.")
        String ageGroup,
        @NotBlank(message = "상세 주소는 필수입니다.")
        @Size(max = 300, message = "상세 주소는 300자 이하여야 합니다.")
        String detailAddress,
        @Size(max = 100, message = "비상 연락처는 100자 이하여야 합니다.")
        String emergencyContact,

        @NotNull(message = "최종 학력은 필수입니다.")
        ProfileHighestEducation highestEducation,
        @NotNull(message = "졸업 여부는 필수입니다.")
        ProfileGraduationStatus graduationStatus,
        List<ProfileEducationEntryDto> educationEntries,
        @NotBlank(message = "주요 경력은 필수입니다.")
        @Size(max = 500, message = "주요 경력은 500자 이하여야 합니다.")
        String majorCareer,
        List<ProfileCareerEntryDto> careerEntries,
        String careerDetail,
        List<ProfileProjectEntryDto> projectEntries,
        String projectExperience,
        @Size(max = 500, message = "경력 공백 사유는 500자 이하여야 합니다.")
        String careerGapReason,

        @NotBlank(message = "지원 직무는 필수입니다.")
        @Size(max = 200, message = "지원 직무는 200자 이하여야 합니다.")
        String targetJob,
        @NotEmpty(message = "보유 기술/역량은 1개 이상 필요합니다.")
        @Size(max = 100, message = "보유 기술/역량은 최대 100개까지 입력할 수 있습니다.")
        List<@NotBlank(message = "보유 기술/역량에는 빈 값을 넣을 수 없습니다.") String> skills,
        List<ProfileCertificationEntryDto> certificationEntries,
        List<String> certifications,
        List<ProfileLanguageEntryDto> languageEntries,
        List<ProfilePortfolioEntryDto> portfolioEntries,
        @Size(max = 500, message = "포트폴리오 URL은 500자 이하여야 합니다.")
        String portfolioUrl,
        List<ProfileAwardEntryDto> awardEntries,
        String awards,
        List<ProfileTrainingEntryDto> trainingEntries,
        String trainings,

        ProfileDisabilitySeverity disabilitySeverity,
        Boolean disabilityRegisteredYn,
        @NotNull(message = "민감정보 수집·이용 동의 여부를 선택해 주세요.")
        Boolean sensitiveInfoConsentYn,
        String disabilityDescription,
        String assistiveDevices,
        String workSupportRequirements,

        ProfileWorkAvailability workAvailability,
        @NotEmpty(message = "근무 형태는 1개 이상 필요합니다.")
        @Size(max = 20, message = "근무 형태는 최대 20개까지 선택할 수 있습니다.")
        List<@NotNull(message = "근무 형태에는 빈 값을 넣을 수 없습니다.") ProfileWorkType> workTypes,
        @Size(max = 120, message = "희망 연봉은 120자 이하여야 합니다.")
        String expectedSalary,
        ProfileWorkTimePreference workTimePreference,
        Boolean remoteAvailableYn,

        @NotBlank(message = "자기소개는 필수입니다.")
        String selfIntroduction,
        String motivation,
        String jobFitDescription,
        String careerGoal,
        String strengthsWeaknesses,

        ProfileMilitaryService militaryService,
        Boolean patrioticVeteranYn,
        @Size(max = 200, message = "유입 경로는 200자 이하여야 합니다.")
        String referrer,
        @Size(max = 500, message = "SNS URL은 500자 이하여야 합니다.")
        String snsUrl
) {

    @AssertTrue(message = "민감정보 처리에 동의한 경우 장애 유형, 장애 정도, 장애인 등록 여부를 모두 입력해 주세요.")
    public boolean isSensitiveInfoCompleteWhenConsented() {
        return !Boolean.TRUE.equals(sensitiveInfoConsentYn)
                || (disabilityType != null && disabilitySeverity != null && disabilityRegisteredYn != null);
    }
}
