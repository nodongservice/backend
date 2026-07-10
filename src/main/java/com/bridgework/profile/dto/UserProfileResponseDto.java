package com.bridgework.profile.dto;

import com.bridgework.auth.entity.GenderType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record UserProfileResponseDto(
        Long profileId,
        Long userId,
        boolean isDefault,
        String desiredJob,
        String commuteRange,
        List<String> preferredWorkEnvironments,
        List<String> avoidedWorkEnvironments,
        List<String> requiredSupports,
        String disabilityType,
        String careerSummary,
        String educationSummary,
        String employmentTypeSummary,
        String profileName,

        String fullName,
        String contactPhone,
        String contactEmail,
        LocalDate birthDate,
        GenderType genderType,
        String ageGroup,
        String detailAddress,
        String emergencyContact,

        String highestEducation,
        String graduationStatus,
        List<ProfileEducationEntryDto> educationEntries,
        String majorCareer,
        List<ProfileCareerEntryDto> careerEntries,
        String careerDetail,
        List<ProfileProjectEntryDto> projectEntries,
        String projectExperience,
        String careerGapReason,

        String targetJob,
        List<String> skills,
        List<ProfileCertificationEntryDto> certificationEntries,
        List<String> certifications,
        List<ProfileLanguageEntryDto> languageEntries,
        List<ProfilePortfolioEntryDto> portfolioEntries,
        String portfolioUrl,
        List<ProfileAwardEntryDto> awardEntries,
        String awards,
        List<ProfileTrainingEntryDto> trainingEntries,
        String trainings,

        String disabilitySeverity,
        Boolean disabilityRegisteredYn,
        Boolean sensitiveInfoConsentYn,
        String disabilityDescription,
        String assistiveDevices,
        String workSupportRequirements,

        String workAvailability,
        List<String> workTypes,
        String expectedSalary,
        String workTimePreference,
        Boolean remoteAvailableYn,

        String selfIntroduction,
        String motivation,
        String jobFitDescription,
        String careerGoal,
        String strengthsWeaknesses,

        String militaryService,
        Boolean patrioticVeteranYn,
        String referrer,
        String snsUrl,

        List<String> aiJobTags,
        List<String> aiEnvironmentTags,
        List<String> aiSupportTags,
        Double homeLat,
        Double homeLng,
        String homeGeocodedAddress,
        OffsetDateTime updatedAt
) {
}
