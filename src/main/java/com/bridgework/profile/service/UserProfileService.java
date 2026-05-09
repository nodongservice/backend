package com.bridgework.profile.service;

import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import com.bridgework.profile.entity.UserProfile;
import com.bridgework.profile.enums.LabeledEnum;
import com.bridgework.profile.exception.ProfileDomainException;
import com.bridgework.profile.exception.UserProfileNotFoundException;
import com.bridgework.profile.repository.UserProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserProfileService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final int MAX_PROFILE_COUNT = 3;

    private final UserProfileRepository userProfileRepository;
    private final AppUserRepository appUserRepository;
    private final ProfileAiTagService profileAiTagService;
    private final ObjectMapper objectMapper;

    public UserProfileService(UserProfileRepository userProfileRepository,
                              AppUserRepository appUserRepository,
                              ProfileAiTagService profileAiTagService,
                              ObjectMapper objectMapper) {
        this.userProfileRepository = userProfileRepository;
        this.appUserRepository = appUserRepository;
        this.profileAiTagService = profileAiTagService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UserProfileResponseDto create(Long userId, UserProfileUpsertRequestDto request) {
        validateBirthDateOrAgeGroup(request);
        AppUser user = loadUser(userId);

        long profileCount = userProfileRepository.countByUser_Id(userId);
        if (profileCount >= MAX_PROFILE_COUNT) {
            throw new ProfileDomainException(
                    "PROFILE_LIMIT_EXCEEDED",
                    HttpStatus.BAD_REQUEST,
                    "프로필은 최대 3개까지 생성할 수 있습니다."
            );
        }

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setDefault(profileCount == 0);
        applyRequestToProfile(profile, request);

        UserProfile savedProfile = userProfileRepository.save(profile);
        return toResponse(savedProfile);
    }

    @Transactional
    public UserProfileResponseDto update(Long userId, Long profileId, UserProfileUpsertRequestDto request) {
        validateBirthDateOrAgeGroup(request);
        UserProfile profile = userProfileRepository.findByIdAndUser_Id(profileId, userId)
                .orElseThrow(() -> new UserProfileNotFoundException(profileId));

        applyRequestToProfile(profile, request);
        UserProfile savedProfile = userProfileRepository.save(profile);
        return toResponse(savedProfile);
    }

    @Transactional
    public void delete(Long userId, Long profileId) {
        UserProfile profile = userProfileRepository.findByIdAndUser_Id(profileId, userId)
                .orElseThrow(() -> new UserProfileNotFoundException(profileId));

        long profileCount = userProfileRepository.countByUser_Id(userId);
        if (profileCount <= 1) {
            throw new ProfileDomainException(
                    "LAST_PROFILE_DELETE_NOT_ALLOWED",
                    HttpStatus.BAD_REQUEST,
                    "기본 프로필 1개는 필수입니다."
            );
        }

        if (profile.isDefault()) {
            throw new ProfileDomainException(
                    "DEFAULT_PROFILE_DELETE_NOT_ALLOWED",
                    HttpStatus.BAD_REQUEST,
                    "기본 프로필은 삭제할 수 없습니다. 다른 프로필을 기본으로 변경한 뒤 삭제하세요."
            );
        }

        userProfileRepository.delete(profile);
    }

    @Transactional
    public UserProfileResponseDto setDefault(Long userId, Long profileId) {
        List<UserProfile> profiles = userProfileRepository.findByUser_IdOrderByIsDefaultDescUpdatedAtDesc(userId);
        if (profiles.isEmpty()) {
            throw new UserProfileNotFoundException(profileId);
        }

        UserProfile targetProfile = profiles.stream()
                .filter(profile -> profile.getId().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new UserProfileNotFoundException(profileId));

        if (targetProfile.isDefault()) {
            return toResponse(targetProfile);
        }

        // 기본 프로필은 사용자당 1개만 유지한다.
        for (UserProfile profile : profiles) {
            profile.setDefault(profile.getId().equals(profileId));
        }

        userProfileRepository.saveAll(profiles);
        return toResponse(targetProfile);
    }

    public List<UserProfileResponseDto> getProfiles(Long userId) {
        return userProfileRepository.findByUser_IdOrderByIsDefaultDescUpdatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UserProfileResponseDto getProfile(Long userId, Long profileId) {
        UserProfile profile = userProfileRepository.findByIdAndUser_Id(profileId, userId)
                .orElseThrow(() -> new UserProfileNotFoundException(profileId));
        return toResponse(profile);
    }

    private AppUser loadUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ProfileDomainException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private void applyRequestToProfile(UserProfile profile, UserProfileUpsertRequestDto request) {
        ProfileAiTags profileAiTags = profileAiTagService.buildTags(request);

        String preferredWorkEnvironmentsJson = toJson(request.preferredWorkEnvironments());
        String avoidedWorkEnvironmentsJson = toJson(request.avoidedWorkEnvironments());
        String requiredSupportsJson = toJson(request.requiredSupports());
        String skillsJson = toJson(request.skills());
        String certificationsJson = toJson(request.certifications());
        String workTypesJson = toJsonLabeledEnum(request.workTypes());

        String aiJobTagsJson = toJson(profileAiTags.jobTags());
        String aiEnvironmentTagsJson = toJson(profileAiTags.environmentTags());
        String aiSupportTagsJson = toJson(profileAiTags.supportTags());

        profile.updateFromRequest(
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
    }

    private void validateBirthDateOrAgeGroup(UserProfileUpsertRequestDto request) {
        if (request.birthDate() == null) {
            throw new ProfileDomainException(
                    "BIRTH_DATE_REQUIRED",
                    HttpStatus.BAD_REQUEST,
                    "생년월일은 필수입니다."
            );
        }
    }

    private UserProfileResponseDto toResponse(UserProfile profile) {
        return new UserProfileResponseDto(
                profile.getId(),
                profile.getUser().getId(),
                profile.isDefault(),
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
                profile.getGenderType(),
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
            throw new ProfileDomainException(
                    "PROFILE_JSON_SERIALIZATION_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "프로필 데이터 직렬화에 실패했습니다."
            );
        }
    }

    private String toJsonLabeledEnum(List<? extends LabeledEnum> values) {
        List<String> labels = values == null
                ? List.of()
                : values.stream().map(value -> ((Enum<?>) value).name()).toList();
        return toJson(labels);
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
