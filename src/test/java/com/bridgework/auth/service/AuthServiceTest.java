package com.bridgework.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.dto.SignupCompleteRequestDto;
import com.bridgework.auth.dto.SocialLoginRequestDto;
import com.bridgework.auth.dto.SocialLoginResponseDto;
import com.bridgework.auth.dto.TokenPairResponseDto;
import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.SocialProvider;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.exception.InvalidRefreshTokenException;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.auth.security.JwtTokenProvider;
import com.bridgework.auth.security.ParsedJwtToken;
import com.bridgework.common.notification.DiscordNotifierService;
import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import com.bridgework.profile.repository.UserProfileRepository;
import com.bridgework.profile.service.UserProfileService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private SocialOAuthService socialOAuthService;
    @Mock
    private SignupSessionStoreService signupSessionStoreService;
    @Mock
    private RefreshTokenStoreService refreshTokenStoreService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserProfileService userProfileService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private DiscordNotifierService discordNotifierService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        BridgeWorkAuthProperties authProperties = new BridgeWorkAuthProperties();
        authProperties.getJwt().setRefreshTokenValidity(Duration.ofDays(14));

        authService = new AuthService(
                appUserRepository,
                socialOAuthService,
                signupSessionStoreService,
                refreshTokenStoreService,
                jwtTokenProvider,
                authProperties,
                userProfileService,
                userProfileRepository,
                discordNotifierService
        );
    }

    @Test
    void socialLogin_whenFirstLogin_thenReturnsSignupRequired() {
        SocialLoginRequestDto request = new SocialLoginRequestDto(
                SocialProvider.KAKAO,
                "oauth-code",
                "http://localhost:3000/auth/kakao/callback",
                null
        );
        SocialUserProfile socialProfile = new SocialUserProfile(
                SocialProvider.KAKAO,
                "kakao-user-1",
                "user@example.com",
                "홍길동"
        );

        when(socialOAuthService.fetchUserProfile(SocialProvider.KAKAO, "oauth-code", request.redirectUri(), null))
                .thenReturn(socialProfile);
        when(appUserRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-user-1"))
                .thenReturn(Optional.empty());
        when(signupSessionStoreService.createSession(any(SocialSignupSessionData.class))).thenReturn("signup-token");

        SocialLoginResponseDto response = authService.socialLogin(request);

        assertThat(response.signupRequired()).isTrue();
        assertThat(response.signupToken()).isEqualTo("signup-token");
        assertThat(response.tokenPair()).isNull();
        verify(jwtTokenProvider, never()).issueTokenPair(any(), any());
    }

    @Test
    void completeSignup_whenValidRequest_thenSavesUserAndIssuesToken() {
        SignupCompleteRequestDto request = new SignupCompleteRequestDto(
                "signup-token",
                null,
                onboardingRequest()
        );
        SocialSignupSessionData sessionData = new SocialSignupSessionData(
                SocialProvider.KAKAO,
                "kakao-user-1",
                "SOCIAL@EXAMPLE.COM",
                "소셜이름"
        );
        OffsetDateTime now = OffsetDateTime.now();
        JwtTokenPair tokenPair = new JwtTokenPair(
                "access-token",
                "refresh-token",
                "refresh-id",
                now.plusMinutes(15),
                now.plusDays(14)
        );

        when(signupSessionStoreService.getRequiredSession("signup-token")).thenReturn(sessionData);
        when(appUserRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-user-1"))
                .thenReturn(Optional.empty());
        when(appUserRepository.existsByEmail("social@example.com")).thenReturn(false);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0, AppUser.class);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtTokenProvider.issueTokenPair(1L, UserRole.USER)).thenReturn(tokenPair);

        TokenPairResponseDto response = authService.completeSignup(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(signupSessionStoreService).deleteSession("signup-token");
        verify(refreshTokenStoreService).save(eq(1L), eq("refresh-id"), eq("refresh-token"), any(Duration.class));

        verify(appUserRepository).save(any(AppUser.class));
        verify(userProfileService).create(eq(1L), any(UserProfileUpsertRequestDto.class));
        verify(discordNotifierService).notifySignupCompleted(eq("social@example.com"), eq(0L));
    }

    @Test
    void refreshToken_whenTokenTypeIsNotRefresh_thenThrows() {
        when(jwtTokenProvider.parse("access-like-token"))
                .thenReturn(new ParsedJwtToken(1L, UserRole.USER, "token-id", JwtTokenProvider.TOKEN_TYPE_ACCESS));

        assertThatThrownBy(() -> authService.refreshToken("access-like-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_whenUserMatchesRefreshTokenOwner_thenDeletesRefreshToken() {
        when(jwtTokenProvider.parse("refresh-token"))
                .thenReturn(new ParsedJwtToken(1L, UserRole.USER, "refresh-id", JwtTokenProvider.TOKEN_TYPE_REFRESH));

        authService.logout(1L, "refresh-token");

        verify(refreshTokenStoreService).delete(1L, "refresh-id");
    }

    private UserProfileUpsertRequestDto onboardingRequest() {
        return new UserProfileUpsertRequestDto(
                "사무보조",
                "30분",
                java.util.List.of("실내"),
                java.util.List.of("소음"),
                java.util.List.of("출입구 경사로"),
                "지체",
                "사무보조 3년",
                "대졸",
                "정규직",
                "홍길동",
                "010-1234-5678",
                "social@example.com",
                java.time.LocalDate.of(1990, 1, 1),
                null,
                "서울",
                "강남구",
                null,
                null,
                "대졸",
                "졸업",
                "A사 사무보조",
                null,
                null,
                null,
                "사무보조",
                java.util.List.of("엑셀"),
                java.util.List.of("컴활"),
                null,
                null,
                null,
                true,
                "중증",
                true,
                null,
                null,
                null,
                "즉시",
                java.util.List.of("정규직"),
                null,
                null,
                null,
                null,
                "자기소개",
                "지원동기",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
