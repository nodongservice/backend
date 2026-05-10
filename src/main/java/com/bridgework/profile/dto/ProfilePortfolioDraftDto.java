package com.bridgework.profile.dto;

import java.util.List;

public record ProfilePortfolioDraftDto(
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
        String birthDate,
        String genderType,
        String ageGroup,
        String detailAddress,
        String emergencyContact,

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
        String snsUrl
) {
}
