package com.bridgework.profile.dto;

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

        String fullName,
        String contactPhone,
        String contactEmail,
        LocalDate birthDate,
        String ageGroup,
        String residenceRegion,
        String detailAddress,
        String emergencyContact,
        String profileImageUrl,

        String highestEducation,
        String graduationStatus,
        String majorCareer,
        String careerDetail,
        String projectExperience,
        String careerGapReason,

        String targetJob,
        List<String> skills,
        List<String> certifications,
        String portfolioUrl,
        String awards,
        String trainings,

        Boolean disabilityYn,
        String disabilitySeverity,
        Boolean disabilityRegisteredYn,
        String disabilityDescription,
        String assistiveDevices,
        String workSupportRequirements,

        String workAvailability,
        List<String> workTypes,
        String expectedSalary,
        String workTimePreference,
        Boolean remoteAvailableYn,
        String mobilityRange,

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
        OffsetDateTime updatedAt
) {
}
