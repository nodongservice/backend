package com.bridgework.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.GenderType;
import com.bridgework.auth.entity.UserStatus;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.common.exception.BridgeWorkDomainException;
import com.bridgework.profile.dto.ProfileCareerEntryDto;
import com.bridgework.profile.dto.ProfileEducationEntryDto;
import com.bridgework.profile.dto.ProfileProjectEntryDto;
import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import com.bridgework.profile.entity.UserProfile;
import com.bridgework.profile.enums.ProfileDisabilitySeverity;
import com.bridgework.profile.enums.ProfileDisabilityType;
import com.bridgework.profile.enums.ProfileGraduationStatus;
import com.bridgework.profile.enums.ProfileHighestEducation;
import com.bridgework.profile.enums.ProfileMilitaryService;
import com.bridgework.profile.enums.ProfileWorkAvailability;
import com.bridgework.profile.enums.ProfileWorkTimePreference;
import com.bridgework.profile.enums.ProfileWorkType;
import com.bridgework.profile.exception.UserProfileNotFoundException;
import com.bridgework.profile.repository.UserProfileRepository;
import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.normalized.NaverGeocodingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ProfileAiTagService profileAiTagService;

    private UserProfileService userProfileService;
    private ObjectMapper objectMapper;
    private Validator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        userProfileService = new UserProfileService(
                userProfileRepository,
                appUserRepository,
                profileAiTagService,
                objectMapper,
                new NaverGeocodingService(null, new ObjectMapper()),
                new BridgeWorkSyncProperties()
        );
    }

    @Test
    void create_whenBirthDateAndAgeGroupAreMissing_thenThrows() {
        UserProfileUpsertRequestDto request = baseRequest(null, null, "테스트 프로필");

        assertThatThrownBy(() -> userProfileService.createWithResolvedHomeCoordinates(1L, request, Optional.empty()))
                .isInstanceOf(BridgeWorkDomainException.class)
                .hasMessage("생년월일은 필수입니다.");
    }

    @Test
    void requestValidation_rejectsInvalidPhoneAndOversizedNestedEntry() throws Exception {
        UserProfileUpsertRequestDto baseRequest = baseRequest(
                LocalDate.of(1995, 5, 10),
                null,
                "테스트 프로필"
        );
        ObjectNode invalidJson = objectMapper.valueToTree(baseRequest);
        invalidJson.put("contactPhone", "1234");
        ((ObjectNode) invalidJson.withArray("educationEntries").get(0))
                .put("schoolName", "가".repeat(301));
        UserProfileUpsertRequestDto invalidRequest = objectMapper.treeToValue(
                invalidJson,
                UserProfileUpsertRequestDto.class
        );

        var violations = validator.validate(invalidRequest);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("contactPhone", "educationEntries[0].schoolName");
    }

    @Test
    void create_whenProfileCountReachedLimit_thenThrows() {
        UserProfileUpsertRequestDto request = baseRequest(LocalDate.of(1995, 5, 10), null, "테스트 프로필");
        AppUser user = user(1L);

        when(appUserRepository.findByIdAndStatusForUpdate(1L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(userProfileRepository.countByUser_Id(1L)).thenReturn(3L);

        assertThatThrownBy(() -> userProfileService.createWithResolvedHomeCoordinates(1L, request, Optional.empty()))
                .isInstanceOf(BridgeWorkDomainException.class)
                .hasMessage("프로필은 최대 3개까지 생성할 수 있습니다.");
    }

    @Test
    void create_firstProfile_shouldBeDefault() {
        UserProfileUpsertRequestDto request = baseRequest(LocalDate.of(1995, 5, 10), null, "테스트 프로필");
        AppUser user = user(1L);
        ProfileAiTags tags = new ProfileAiTags(
                List.of("사무보조", "엑셀"),
                List.of("주간", "실내"),
                List.of("휠체어 접근")
        );

        when(appUserRepository.findByIdAndStatusForUpdate(1L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(userProfileRepository.countByUser_Id(1L)).thenReturn(0L);
        when(profileAiTagService.buildTags(eq(request), anyList(), any(), any(), any(), any())).thenReturn(tags);
        when(userProfileRepository.saveAndFlush(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0, UserProfile.class);
            ReflectionTestUtils.setField(profile, "id", 10L);
            return profile;
        });

        UserProfileResponseDto response = userProfileService.createWithResolvedHomeCoordinates(1L, request, Optional.empty());

        assertThat(response.profileId()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.isDefault()).isTrue();
        assertThat(response.profileName()).isEqualTo("테스트 프로필");
        verify(userProfileRepository).saveAndFlush(any(UserProfile.class));
    }

    @Test
    void create_firstProfile_whenProfileNameMissing_thenSetsDefaultGeneratedName() {
        UserProfileUpsertRequestDto request = baseRequest(LocalDate.of(1995, 5, 10), null, null);
        AppUser user = user(1L);
        ProfileAiTags tags = new ProfileAiTags(
                List.of("사무보조", "엑셀"),
                List.of("주간", "실내"),
                List.of("휠체어 접근")
        );

        when(appUserRepository.findByIdAndStatusForUpdate(1L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(userProfileRepository.countByUser_Id(1L)).thenReturn(0L);
        when(profileAiTagService.buildTags(eq(request), anyList(), any(), any(), any(), any())).thenReturn(tags);
        when(userProfileRepository.saveAndFlush(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0, UserProfile.class);
            ReflectionTestUtils.setField(profile, "id", 10L);
            return profile;
        });

        UserProfileResponseDto response = userProfileService.createWithResolvedHomeCoordinates(1L, request, Optional.empty());

        assertThat(response.profileName()).isEqualTo("기본 생성 프로필");
    }

    @Test
    void create_withoutSensitiveConsent_doesNotPersistOrDeriveSensitiveInformation() {
        UserProfileUpsertRequestDto request = baseRequest(LocalDate.of(1995, 5, 10), null, "선택정보 없는 프로필", false);
        AppUser user = user(1L);
        ProfileAiTags tags = new ProfileAiTags(List.of("사무보조"), List.of("주간"), List.of());

        when(appUserRepository.findByIdAndStatusForUpdate(1L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(userProfileRepository.countByUser_Id(1L)).thenReturn(0L);
        when(profileAiTagService.buildTags(eq(request), eq(List.of()), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(tags);
        when(userProfileRepository.saveAndFlush(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0, UserProfile.class);
            ReflectionTestUtils.setField(profile, "id", 10L);
            return profile;
        });

        UserProfileResponseDto response = userProfileService.createWithResolvedHomeCoordinates(1L, request, Optional.empty());

        assertThat(response.sensitiveInfoConsentYn()).isFalse();
        assertThat(response.disabilityType()).isNull();
        assertThat(response.disabilitySeverity()).isNull();
        assertThat(response.disabilityRegisteredYn()).isNull();
        assertThat(response.requiredSupports()).isEmpty();
    }

    @Test
    void update_whenGeocodingTemporarilyFailsForUnchangedAddress_preservesExistingCoordinates() {
        AppUser user = user(1L);
        UserProfile profile = profile(11L, user, true);
        profile.updateHomeCoordinates(37.4979, 127.0276, "서울 강남구");
        UserProfileUpsertRequestDto request = baseRequest(LocalDate.of(1995, 5, 10), null, "테스트 프로필");

        when(appUserRepository.findByIdAndStatusForUpdate(1L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByIdAndUser_Id(11L, 1L)).thenReturn(Optional.of(profile));
        when(profileAiTagService.buildTags(eq(request), anyList(), any(), any(), any(), any()))
                .thenReturn(new ProfileAiTags(List.of("사무보조"), List.of("주간"), List.of("휠체어 접근")));
        when(userProfileRepository.saveAndFlush(profile)).thenReturn(profile);

        UserProfileResponseDto response = userProfileService.updateWithResolvedHomeCoordinates(
                1L,
                11L,
                request,
                Optional.empty()
        );

        assertThat(response.homeLat()).isEqualTo(37.4979);
        assertThat(response.homeLng()).isEqualTo(127.0276);
        assertThat(response.homeGeocodedAddress()).isEqualTo("서울 강남구");
    }

    @Test
    void setDefault_shouldSwitchDefaultProfile() {
        AppUser user = user(1L);
        UserProfile defaultProfile = profile(11L, user, true);
        UserProfile secondProfile = profile(12L, user, false);

        when(appUserRepository.findByIdAndStatusForUpdate(1L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUser_IdOrderByIsDefaultDescUpdatedAtDesc(1L))
                .thenReturn(List.of(defaultProfile, secondProfile));

        UserProfileResponseDto response = userProfileService.setDefault(1L, 12L);

        assertThat(response.profileId()).isEqualTo(12L);
        assertThat(defaultProfile.isDefault()).isFalse();
        assertThat(secondProfile.isDefault()).isTrue();
        InOrder saveOrder = inOrder(userProfileRepository);
        saveOrder.verify(userProfileRepository).saveAndFlush(defaultProfile);
        saveOrder.verify(userProfileRepository).saveAndFlush(secondProfile);
    }

    @Test
    void delete_whenDefaultProfile_thenThrows() {
        AppUser user = user(1L);
        UserProfile defaultProfile = profile(11L, user, true);

        when(appUserRepository.findByIdAndStatusForUpdate(1L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByIdAndUser_Id(11L, 1L)).thenReturn(Optional.of(defaultProfile));
        when(userProfileRepository.countByUser_Id(1L)).thenReturn(2L);

        assertThatThrownBy(() -> userProfileService.delete(1L, 11L))
                .isInstanceOf(BridgeWorkDomainException.class)
                .hasMessageContaining("기본 프로필은 삭제할 수 없습니다");
    }

    @Test
    void getProfile_whenMissing_thenThrows() {
        when(userProfileRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getProfile(1L, 99L))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user@example.com");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private UserProfile profile(Long profileId, AppUser user, boolean isDefault) {
        UserProfile profile = new UserProfile();
        ReflectionTestUtils.setField(profile, "id", profileId);
        profile.setUser(user);
        profile.setDefault(isDefault);
        profile.updateFromRequest(
                baseRequest(LocalDate.of(1995, 5, 10), null, "테스트 프로필"),
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]",
                "[]"
        );
        profile.updatePrivateDetails(
                "홍길동",
                "010-1111-2222",
                null,
                LocalDate.of(1995, 5, 10),
                GenderType.MALE,
                "강남구",
                "010-9999-9999"
        );
        profile.updateSensitiveInfo(
                "[]",
                ProfileDisabilityType.PHYSICAL.name(),
                ProfileDisabilitySeverity.SEVERE.name(),
                true,
                true,
                "이동 시 보조 필요",
                "휠체어",
                "엘리베이터"
        );
        return profile;
    }

    private UserProfileUpsertRequestDto baseRequest(LocalDate birthDate, String ageGroup, String profileName) {
        return baseRequest(birthDate, ageGroup, profileName, true);
    }

    private UserProfileUpsertRequestDto baseRequest(LocalDate birthDate, String ageGroup, String profileName, boolean sensitiveConsent) {
        return new UserProfileUpsertRequestDto(
                "사무보조",
                "30분",
                List.of("실내", "주간"),
                List.of("소음"),
                List.of("휠체어 접근"),
                ProfileDisabilityType.PHYSICAL,
                "사무 경력 3년",
                "대졸",
                "정규직",
                profileName,

                "홍길동",
                "010-1111-2222",
                "hong@example.com",
                birthDate,
                GenderType.MALE,
                ageGroup,
                "강남구",
                "010-9999-9999",

                ProfileHighestEducation.BACHELOR,
                ProfileGraduationStatus.GRADUATED,
                List.of(new ProfileEducationEntryDto("COLLEGE_4", "테스트대학교", "2011", "2015", "GRADUATED")),
                "A사 사무보조",
                List.of(new ProfileCareerEntryDto("A사", "경영지원팀", "2018.01", "2021.12", "문서 관리")),
                "문서 관리",
                List.of(new ProfileProjectEntryDto("BOOTCAMP", "내부 시스템 개선", "2020.03", "2020.06", "내부 시스템 개선")),
                "내부 시스템 개선",
                "건강 회복",

                "사무보조",
                List.of("엑셀", "문서작성"),
                List.of(),
                List.of("컴활"),
                List.of(),
                List.of(),
                "https://example.com/portfolio",
                List.of(),
                "우수사원",
                List.of(),
                "직무교육",
                ProfileDisabilitySeverity.SEVERE,
                true,
                sensitiveConsent,
                "이동 시 보조 필요",
                "수동 휠체어",
                "출입구 경사로",

                ProfileWorkAvailability.IMMEDIATE,
                List.of(ProfileWorkType.FULL_TIME),
                "3000만원",
                ProfileWorkTimePreference.DAYTIME,
                false,

                "문서 관리에 강점이 있습니다.",
                "장기 근속이 가능한 업무를 원합니다.",
                "정확한 문서작성 역량",
                "행정 전문가",
                "꼼꼼함/완벽주의",

                ProfileMilitaryService.NOT_APPLICABLE,
                false,
                "지인 추천",
                "https://example.com/sns"
        );
    }
}
