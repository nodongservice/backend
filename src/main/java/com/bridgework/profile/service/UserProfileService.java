package com.bridgework.profile.service;

import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.UserStatus;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.profile.dto.ProfileCareerEntryDto;
import com.bridgework.profile.dto.ProfileCertificationEntryDto;
import com.bridgework.profile.dto.ProfileEducationEntryDto;
import com.bridgework.profile.dto.ProfileLanguageEntryDto;
import com.bridgework.profile.dto.ProfilePortfolioEntryDto;
import com.bridgework.profile.dto.ProfileProjectEntryDto;
import com.bridgework.profile.dto.ProfileAwardEntryDto;
import com.bridgework.profile.dto.ProfileTrainingEntryDto;
import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import com.bridgework.profile.entity.UserProfile;
import com.bridgework.profile.enums.LabeledEnum;
import com.bridgework.profile.enums.ProfileDisabilitySeverity;
import com.bridgework.profile.enums.ProfileDisabilityType;
import com.bridgework.profile.exception.ProfileDomainException;
import com.bridgework.profile.exception.UserProfileNotFoundException;
import com.bridgework.profile.repository.UserProfileRepository;
import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.normalized.NaverGeocodingService;
import com.bridgework.sync.normalized.NormalizedGeoPoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserProfileService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileEducationEntryDto>> EDUCATION_ENTRY_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileCareerEntryDto>> CAREER_ENTRY_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileProjectEntryDto>> PROJECT_ENTRY_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileCertificationEntryDto>> CERTIFICATION_ENTRY_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileLanguageEntryDto>> LANGUAGE_ENTRY_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfilePortfolioEntryDto>> PORTFOLIO_ENTRY_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileAwardEntryDto>> AWARD_ENTRY_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileTrainingEntryDto>> TRAINING_ENTRY_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final int MAX_PROFILE_COUNT = 3;
    private static final String DEFAULT_CREATED_PROFILE_NAME = "기본 생성 프로필";

    private final UserProfileRepository userProfileRepository;
    private final AppUserRepository appUserRepository;
    private final ProfileAiTagService profileAiTagService;
    private final ObjectMapper objectMapper;
    private final NaverGeocodingService naverGeocodingService;
    private final BridgeWorkSyncProperties syncProperties;

    public UserProfileService(UserProfileRepository userProfileRepository,
                              AppUserRepository appUserRepository,
                              ProfileAiTagService profileAiTagService,
                              ObjectMapper objectMapper,
                              NaverGeocodingService naverGeocodingService,
                              BridgeWorkSyncProperties syncProperties) {
        this.userProfileRepository = userProfileRepository;
        this.appUserRepository = appUserRepository;
        this.profileAiTagService = profileAiTagService;
        this.objectMapper = objectMapper;
        this.naverGeocodingService = naverGeocodingService;
        this.syncProperties = syncProperties;
    }

    @Transactional
    public UserProfileResponseDto createWithResolvedHomeCoordinates(Long userId,
                                                                    UserProfileUpsertRequestDto request,
                                                                    Optional<NormalizedGeoPoint> homeGeoPoint) {
        validateBirthDateOrAgeGroup(request);
        AppUser user = loadUserForUpdate(userId);

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
        boolean isDefaultProfile = profileCount == 0;
        profile.setDefault(isDefaultProfile);
        profile.setProfileName(resolveProfileName(request.profileName(), isDefaultProfile, profileCount + 1, null));
        applyRequestToProfile(profile, request, homeGeoPoint);

        UserProfile savedProfile = userProfileRepository.saveAndFlush(profile);
        return toResponse(savedProfile);
    }

    @Transactional
    public UserProfileResponseDto updateWithResolvedHomeCoordinates(Long userId,
                                                                    Long profileId,
                                                                    UserProfileUpsertRequestDto request,
                                                                    Optional<NormalizedGeoPoint> homeGeoPoint) {
        validateBirthDateOrAgeGroup(request);
        loadUserForUpdate(userId);
        UserProfile profile = userProfileRepository.findByIdAndUser_Id(profileId, userId)
                .orElseThrow(() -> new UserProfileNotFoundException(profileId));

        profile.setProfileName(resolveProfileName(request.profileName(), profile.isDefault(), null, profile.getProfileName()));
        applyRequestToProfile(profile, request, homeGeoPoint);
        UserProfile savedProfile = userProfileRepository.saveAndFlush(profile);
        return toResponse(savedProfile);
    }

    @Transactional
    public void delete(Long userId, Long profileId) {
        loadUserForUpdate(userId);
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
        loadUserForUpdate(userId);
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
        userProfileRepository.flush();
        return toResponse(targetProfile);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponseDto> getProfiles(Long userId) {
        return userProfileRepository.findByUser_IdOrderByIsDefaultDescUpdatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDto getProfile(Long userId, Long profileId) {
        UserProfile profile = userProfileRepository.findByIdAndUser_Id(profileId, userId)
                .orElseThrow(() -> new UserProfileNotFoundException(profileId));
        return toResponse(profile);
    }

    private AppUser loadUserForUpdate(Long userId) {
        return appUserRepository.findByIdAndStatusForUpdate(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new ProfileDomainException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private void applyRequestToProfile(UserProfile profile,
                                       UserProfileUpsertRequestDto request,
                                       Optional<NormalizedGeoPoint> homeGeoPoint) {
        boolean consented = Boolean.TRUE.equals(request.sensitiveInfoConsentYn());
        List<String> sanitizedRequiredSupports = sanitizeSensitiveList(request.requiredSupports(), consented);
        String sanitizedDisabilityDescription = sanitizeSensitiveText(request.disabilityDescription(), consented);
        String sanitizedAssistiveDevices = sanitizeSensitiveText(request.assistiveDevices(), consented);
        String sanitizedWorkSupportRequirements = sanitizeSensitiveText(request.workSupportRequirements(), consented);
        ProfileDisabilityType sanitizedDisabilityType = consented ? request.disabilityType() : null;
        ProfileDisabilitySeverity sanitizedDisabilitySeverity = consented ? request.disabilitySeverity() : null;
        Boolean sanitizedDisabilityRegisteredYn = consented ? request.disabilityRegisteredYn() : null;

        ProfileAiTags profileAiTags = profileAiTagService.buildTags(
                request,
                sanitizedRequiredSupports,
                sanitizedWorkSupportRequirements,
                sanitizedAssistiveDevices,
                sanitizedDisabilityType,
                sanitizedDisabilitySeverity
        );

        String preferredWorkEnvironmentsJson = toJson(request.preferredWorkEnvironments());
        String avoidedWorkEnvironmentsJson = toJson(request.avoidedWorkEnvironments());
        String requiredSupportsJson = toJson(sanitizedRequiredSupports);
        String skillsJson = toJson(request.skills());
        String certificationsJson = toJson(request.certifications());
        String workTypesJson = toJsonLabeledEnum(request.workTypes());
        String educationEntriesJson = toJsonObjects(request.educationEntries());
        String careerEntriesJson = toJsonObjects(request.careerEntries());
        String projectEntriesJson = toJsonObjects(request.projectEntries());
        String certificationEntriesJson = toJsonObjects(request.certificationEntries());
        String languageEntriesJson = toJsonObjects(request.languageEntries());
        String portfolioEntriesJson = toJsonObjects(request.portfolioEntries());
        String awardEntriesJson = toJsonObjects(request.awardEntries());
        String trainingEntriesJson = toJsonObjects(request.trainingEntries());

        String aiJobTagsJson = toJson(profileAiTags.jobTags());
        String aiEnvironmentTagsJson = toJson(profileAiTags.environmentTags());
        String aiSupportTagsJson = toJson(profileAiTags.supportTags());

        profile.updateFromRequest(
                request,
                preferredWorkEnvironmentsJson,
                avoidedWorkEnvironmentsJson,
                skillsJson,
                certificationsJson,
                workTypesJson,
                educationEntriesJson,
                careerEntriesJson,
                projectEntriesJson,
                certificationEntriesJson,
                languageEntriesJson,
                portfolioEntriesJson,
                awardEntriesJson,
                trainingEntriesJson,
                aiJobTagsJson,
                aiEnvironmentTagsJson,
                aiSupportTagsJson
        );
        profile.updatePrivateDetails(
                request.fullName(),
                request.contactPhone(),
                resolveContactEmailOverride(profile.getUser(), request.contactEmail()),
                request.birthDate(),
                request.genderType(),
                request.detailAddress(),
                request.emergencyContact()
        );
        profile.updateSensitiveInfo(
                requiredSupportsJson,
                sanitizedDisabilityType == null ? null : sanitizedDisabilityType.name(),
                sanitizedDisabilitySeverity == null ? null : sanitizedDisabilitySeverity.name(),
                sanitizedDisabilityRegisteredYn,
                consented,
                sanitizedDisabilityDescription,
                sanitizedAssistiveDevices,
                sanitizedWorkSupportRequirements
        );
        applyHomeCoordinates(profile, homeGeoPoint);
    }

    public Optional<NormalizedGeoPoint> prepareHomeCoordinates(String detailAddress) {
        try {
            return naverGeocodingService.geocode(
                    syncProperties.getNaverGeocodeApiKeyId(),
                    syncProperties.getNaverGeocodeApiKey(),
                    detailAddress
            );
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private void applyHomeCoordinates(UserProfile profile, Optional<NormalizedGeoPoint> geoPoint) {
        if (geoPoint.isPresent()) {
            NormalizedGeoPoint point = geoPoint.get();
            profile.updateHomeCoordinates(point.latitude(), point.longitude(), point.matchedAddress());
            return;
        }
        profile.updateHomeCoordinates(null, null, null);
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

    private String resolveContactEmailOverride(AppUser user, String contactEmail) {
        String normalizedContactEmail = StringUtils.trimWhitespace(contactEmail);
        String accountEmail = user == null ? null : StringUtils.trimWhitespace(user.getEmail());
        if (!StringUtils.hasText(normalizedContactEmail)) {
            return null;
        }
        if (accountEmail != null && normalizedContactEmail.equalsIgnoreCase(accountEmail)) {
            return null;
        }
        return normalizedContactEmail;
    }

    private List<String> sanitizeSensitiveList(List<String> values, boolean consented) {
        return consented ? (values == null ? List.of() : values) : List.of();
    }

    private String sanitizeSensitiveText(String value, boolean consented) {
        return consented ? value : null;
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
                profile.getProfileName(),

                profile.getFullName(),
                profile.getContactPhone(),
                profile.getContactEmail(),
                profile.getBirthDate(),
                profile.getGenderType(),
                profile.getAgeGroup(),
                profile.getDetailAddress(),
                profile.getEmergencyContact(),

                profile.getHighestEducation(),
                profile.getGraduationStatus(),
                readJsonList(profile.getEducationEntriesJson(), EDUCATION_ENTRY_LIST_TYPE_REFERENCE),
                profile.getMajorCareer(),
                readJsonList(profile.getCareerEntriesJson(), CAREER_ENTRY_LIST_TYPE_REFERENCE),
                profile.getCareerDetail(),
                readJsonList(profile.getProjectEntriesJson(), PROJECT_ENTRY_LIST_TYPE_REFERENCE),
                profile.getProjectExperience(),
                profile.getCareerGapReason(),

                profile.getTargetJob(),
                toStringList(profile.getSkillsJson()),
                readJsonList(profile.getCertificationEntriesJson(), CERTIFICATION_ENTRY_LIST_TYPE_REFERENCE),
                toStringList(profile.getCertificationsJson()),
                readJsonList(profile.getLanguageEntriesJson(), LANGUAGE_ENTRY_LIST_TYPE_REFERENCE),
                readJsonList(profile.getPortfolioEntriesJson(), PORTFOLIO_ENTRY_LIST_TYPE_REFERENCE),
                profile.getPortfolioUrl(),
                readJsonList(profile.getAwardEntriesJson(), AWARD_ENTRY_LIST_TYPE_REFERENCE),
                profile.getAwards(),
                readJsonList(profile.getTrainingEntriesJson(), TRAINING_ENTRY_LIST_TYPE_REFERENCE),
                profile.getTrainings(),

                profile.getDisabilitySeverity(),
                profile.getDisabilityRegisteredYn(),
                profile.getSensitiveInfoConsentYn(),
                profile.getDisabilityDescription(),
                profile.getAssistiveDevices(),
                profile.getWorkSupportRequirements(),

                profile.getWorkAvailability(),
                toStringList(profile.getWorkTypesJson()),
                profile.getExpectedSalary(),
                profile.getWorkTimePreference(),
                profile.getRemoteAvailableYn(),

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
                profile.getHomeLat(),
                profile.getHomeLng(),
                profile.getHomeGeocodedAddress(),
                profile.getUpdatedAt()
        );
    }

    private String resolveProfileName(String rawProfileName,
                                      boolean isDefaultProfile,
                                      Long nextProfileOrder,
                                      String currentProfileName) {
        String trimmedProfileName = StringUtils.trimWhitespace(rawProfileName);
        if (StringUtils.hasText(trimmedProfileName)) {
            return trimmedProfileName;
        }

        if (StringUtils.hasText(currentProfileName)) {
            return currentProfileName;
        }

        if (isDefaultProfile) {
            return DEFAULT_CREATED_PROFILE_NAME;
        }

        if (nextProfileOrder != null && nextProfileOrder > 0) {
            return "프로필 " + nextProfileOrder;
        }

        return "프로필";
    }

    private String toJson(List<String> values) {
        return writeJson(values == null ? List.of() : values);
    }

    private String toJsonObjects(List<?> values) {
        return writeJson(values == null ? List.of() : values);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
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
        return readJsonList(json, STRING_LIST_TYPE_REFERENCE);
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeReference) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            // 저장 데이터가 깨진 경우라도 API는 중단하지 않고 빈 목록으로 응답한다.
            return List.of();
        }
    }
}
