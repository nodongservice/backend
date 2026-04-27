package com.bridgework.onboarding.service;

import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.onboarding.dto.OnboardingProfileResponseDto;
import com.bridgework.onboarding.dto.OnboardingProfileUpsertRequestDto;
import com.bridgework.onboarding.entity.OnboardingProfile;
import com.bridgework.onboarding.exception.OnboardingDomainException;
import com.bridgework.onboarding.exception.OnboardingProfileNotFoundException;
import com.bridgework.onboarding.repository.OnboardingProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OnboardingProfileService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final OnboardingProfileRepository onboardingProfileRepository;
    private final AppUserRepository appUserRepository;
    private final OnboardingAiTagService onboardingAiTagService;
    private final ObjectMapper objectMapper;

    public OnboardingProfileService(OnboardingProfileRepository onboardingProfileRepository,
                                    AppUserRepository appUserRepository,
                                    OnboardingAiTagService onboardingAiTagService,
                                    ObjectMapper objectMapper) {
        this.onboardingProfileRepository = onboardingProfileRepository;
        this.appUserRepository = appUserRepository;
        this.onboardingAiTagService = onboardingAiTagService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OnboardingProfileResponseDto upsert(Long userId, OnboardingProfileUpsertRequestDto request) {
        validateBirthDateOrAgeGroup(request);

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new OnboardingDomainException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        OnboardingAiTags onboardingAiTags = onboardingAiTagService.buildTags(request);

        String preferredWorkEnvironmentsJson = toJson(request.preferredWorkEnvironments());
        String avoidedWorkEnvironmentsJson = toJson(request.avoidedWorkEnvironments());
        String requiredSupportsJson = toJson(request.requiredSupports());
        String skillsJson = toJson(request.skills());
        String certificationsJson = toJson(request.certifications());
        String workTypesJson = toJson(request.workTypes());

        String aiJobTagsJson = toJson(onboardingAiTags.jobTags());
        String aiEnvironmentTagsJson = toJson(onboardingAiTags.environmentTags());
        String aiSupportTagsJson = toJson(onboardingAiTags.supportTags());

        OnboardingProfile onboardingProfile = onboardingProfileRepository.findByUser_Id(userId)
                .orElseGet(OnboardingProfile::new);

        onboardingProfile.setUser(user);
        onboardingProfile.updateFromRequest(
                request,
                preferredWorkEnvironmentsJson,
                avoidedWorkEnvironmentsJson,
                requiredSupportsJson,
                skillsJson,
                certificationsJson,
                workTypesJson,
                aiJobTagsJson,
                aiEnvironmentTagsJson,
                aiSupportTagsJson
        );

        OnboardingProfile savedProfile = onboardingProfileRepository.save(onboardingProfile);
        return toResponse(savedProfile);
    }

    public OnboardingProfileResponseDto getByUserId(Long userId) {
        OnboardingProfile onboardingProfile = onboardingProfileRepository.findByUser_Id(userId)
                .orElseThrow(OnboardingProfileNotFoundException::new);

        return toResponse(onboardingProfile);
    }

    private void validateBirthDateOrAgeGroup(OnboardingProfileUpsertRequestDto request) {
        if (request.birthDate() == null && !StringUtils.hasText(request.ageGroup())) {
            throw new OnboardingDomainException(
                    "BIRTH_DATE_OR_AGE_GROUP_REQUIRED",
                    HttpStatus.BAD_REQUEST,
                    "생년월일 또는 연령대 중 하나는 필수입니다."
            );
        }
    }

    private OnboardingProfileResponseDto toResponse(OnboardingProfile profile) {
        return new OnboardingProfileResponseDto(
                profile.getUser().getId(),
                profile.getDesiredJob(),
                profile.getCommuteRange(),
                toStringList(profile.getPreferredWorkEnvironmentsJson()),
                toStringList(profile.getAvoidedWorkEnvironmentsJson()),
                toStringList(profile.getRequiredSupportsJson()),
                profile.getDisabilityType(),
                profile.getCareerSummary(),
                profile.getEducationSummary(),
                profile.getEmploymentTypeSummary(),

                profile.getFullName(),
                profile.getContactPhone(),
                profile.getContactEmail(),
                profile.getBirthDate(),
                profile.getAgeGroup(),
                profile.getResidenceRegion(),
                profile.getDetailAddress(),
                profile.getEmergencyContact(),
                profile.getProfileImageUrl(),

                profile.getHighestEducation(),
                profile.getGraduationStatus(),
                profile.getMajorCareer(),
                profile.getCareerDetail(),
                profile.getProjectExperience(),
                profile.getCareerGapReason(),

                profile.getTargetJob(),
                toStringList(profile.getSkillsJson()),
                toStringList(profile.getCertificationsJson()),
                profile.getPortfolioUrl(),
                profile.getAwards(),
                profile.getTrainings(),

                profile.getDisabilityYn(),
                profile.getDisabilitySeverity(),
                profile.getDisabilityRegisteredYn(),
                profile.getDisabilityDescription(),
                profile.getAssistiveDevices(),
                profile.getWorkSupportRequirements(),

                profile.getWorkAvailability(),
                toStringList(profile.getWorkTypesJson()),
                profile.getExpectedSalary(),
                profile.getWorkTimePreference(),
                profile.getRemoteAvailableYn(),
                profile.getMobilityRange(),

                profile.getSelfIntroduction(),
                profile.getMotivation(),
                profile.getJobFitDescription(),
                profile.getCareerGoal(),
                profile.getStrengthsWeaknesses(),

                profile.getMilitaryService(),
                profile.getPatrioticVeteranYn(),
                profile.getReferrer(),
                profile.getSnsUrl(),

                toStringList(profile.getAiJobTagsJson()),
                toStringList(profile.getAiEnvironmentTagsJson()),
                toStringList(profile.getAiSupportTagsJson()),
                profile.getUpdatedAt()
        );
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new OnboardingDomainException(
                    "ONBOARDING_JSON_SERIALIZATION_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "온보딩 데이터 직렬화에 실패했습니다."
            );
        }
    }

    private List<String> toStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE_REFERENCE);
        } catch (JsonProcessingException exception) {
            // 저장 데이터가 깨진 경우라도 API는 중단하지 않고 빈 목록으로 응답한다.
            return List.of();
        }
    }
}
