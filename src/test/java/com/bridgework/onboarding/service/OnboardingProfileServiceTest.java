package com.bridgework.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.common.exception.BridgeWorkDomainException;
import com.bridgework.onboarding.dto.OnboardingProfileResponseDto;
import com.bridgework.onboarding.dto.OnboardingProfileUpsertRequestDto;
import com.bridgework.onboarding.entity.UserProfile;
import com.bridgework.onboarding.exception.OnboardingProfileNotFoundException;
import com.bridgework.onboarding.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OnboardingProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OnboardingAiTagService onboardingAiTagService;

    private OnboardingProfileService onboardingProfileService;

    @BeforeEach
    void setUp() {
        onboardingProfileService = new OnboardingProfileService(
                userProfileRepository,
                appUserRepository,
                onboardingAiTagService,
                new ObjectMapper()
        );
    }

    @Test
    void create_whenBirthDateAndAgeGroupAreMissing_thenThrows() {
        OnboardingProfileUpsertRequestDto request = baseRequest(null, null);

        assertThatThrownBy(() -> onboardingProfileService.create(1L, request))
                .isInstanceOf(BridgeWorkDomainException.class)
                .hasMessage("생년월일은 필수입니다.");
    }

    @Test
    void create_whenProfileCountReachedLimit_thenThrows() {
        OnboardingProfileUpsertRequestDto request = baseRequest(LocalDate.of(1995, 5, 10), null);
        AppUser user = user(1L);

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userProfileRepository.countByUser_Id(1L)).thenReturn(3L);

        assertThatThrownBy(() -> onboardingProfileService.create(1L, request))
                .isInstanceOf(BridgeWorkDomainException.class)
                .hasMessage("프로필은 최대 3개까지 생성할 수 있습니다.");
    }

    @Test
    void create_firstProfile_shouldBeDefault() {
        OnboardingProfileUpsertRequestDto request = baseRequest(LocalDate.of(1995, 5, 10), null);
        AppUser user = user(1L);
        OnboardingAiTags tags = new OnboardingAiTags(
                List.of("사무보조", "엑셀"),
                List.of("주간", "실내"),
                List.of("휠체어 접근")
        );

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userProfileRepository.countByUser_Id(1L)).thenReturn(0L);
        when(onboardingAiTagService.buildTags(request)).thenReturn(tags);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0, UserProfile.class);
            ReflectionTestUtils.setField(profile, "id", 10L);
            return profile;
        });

        OnboardingProfileResponseDto response = onboardingProfileService.create(1L, request);

        assertThat(response.profileId()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.isDefault()).isTrue();
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void setDefault_shouldSwitchDefaultProfile() {
        AppUser user = user(1L);
        UserProfile defaultProfile = profile(11L, user, true);
        UserProfile secondProfile = profile(12L, user, false);

        when(userProfileRepository.findByUser_IdOrderByIsDefaultDescUpdatedAtDesc(1L))
                .thenReturn(List.of(defaultProfile, secondProfile));

        OnboardingProfileResponseDto response = onboardingProfileService.setDefault(1L, 12L);

        assertThat(response.profileId()).isEqualTo(12L);
        assertThat(defaultProfile.isDefault()).isFalse();
        assertThat(secondProfile.isDefault()).isTrue();
        verify(userProfileRepository).saveAll(any());
    }

    @Test
    void delete_whenDefaultProfile_thenThrows() {
        AppUser user = user(1L);
        UserProfile defaultProfile = profile(11L, user, true);

        when(userProfileRepository.findByIdAndUser_Id(11L, 1L)).thenReturn(Optional.of(defaultProfile));
        when(userProfileRepository.countByUser_Id(1L)).thenReturn(2L);

        assertThatThrownBy(() -> onboardingProfileService.delete(1L, 11L))
                .isInstanceOf(BridgeWorkDomainException.class)
                .hasMessageContaining("기본 프로필은 삭제할 수 없습니다");
    }

    @Test
    void getProfile_whenMissing_thenThrows() {
        when(userProfileRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> onboardingProfileService.getProfile(1L, 99L))
                .isInstanceOf(OnboardingProfileNotFoundException.class);
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserProfile profile(Long profileId, AppUser user, boolean isDefault) {
        UserProfile profile = new UserProfile();
        ReflectionTestUtils.setField(profile, "id", profileId);
        profile.setUser(user);
        profile.setDefault(isDefault);
        profile.updateFromRequest(
                baseRequest(LocalDate.of(1995, 5, 10), null),
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
        return profile;
    }

    private OnboardingProfileUpsertRequestDto baseRequest(LocalDate birthDate, String ageGroup) {
        return new OnboardingProfileUpsertRequestDto(
                "사무보조",
                "30분",
                List.of("실내", "주간"),
                List.of("소음"),
                List.of("휠체어 접근"),
                "지체",
                "사무 경력 3년",
                "대졸",
                "정규직",

                "홍길동",
                "010-1111-2222",
                "hong@example.com",
                birthDate,
                ageGroup,
                "서울",
                "강남구",
                "010-9999-9999",
                "https://example.com/profile.jpg",

                "대학교 졸업",
                "졸업",
                "A사 사무보조",
                "문서 관리",
                "내부 시스템 개선",
                "건강 회복",

                "사무보조",
                List.of("엑셀", "문서작성"),
                List.of("컴활"),
                "https://example.com/portfolio",
                "우수사원",
                "직무교육",

                true,
                "중증",
                true,
                "이동 시 보조 필요",
                "수동 휠체어",
                "출입구 경사로",

                "즉시",
                List.of("정규직"),
                "3000만원",
                "주간",
                false,
                "대중교통 1회 환승",

                "문서 관리에 강점이 있습니다.",
                "장기 근속이 가능한 업무를 원합니다.",
                "정확한 문서작성 역량",
                "행정 전문가",
                "꼼꼼함/완벽주의",

                "해당없음",
                false,
                "지인 추천",
                "https://example.com/sns"
        );
    }
}
