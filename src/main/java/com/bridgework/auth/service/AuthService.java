package com.bridgework.auth.service;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.dto.AuthMeResponseDto;
import com.bridgework.auth.dto.SignupCompleteRequestDto;
import com.bridgework.auth.dto.SocialLoginRequestDto;
import com.bridgework.auth.dto.SocialLoginResponseDto;
import com.bridgework.auth.dto.TokenPairResponseDto;
import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.exception.DuplicateEmailException;
import com.bridgework.auth.exception.InvalidRefreshTokenException;
import com.bridgework.auth.exception.UserNotFoundException;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.auth.security.JwtTokenProvider;
import com.bridgework.auth.security.ParsedJwtToken;
import com.bridgework.common.notification.DiscordNotifierService;
import com.bridgework.profile.repository.UserProfileRepository;
import com.bridgework.profile.service.UserProfileService;
import jakarta.transaction.Transactional;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final SocialOAuthService socialOAuthService;
    private final SignupSessionStoreService signupSessionStoreService;
    private final RefreshTokenStoreService refreshTokenStoreService;
    private final JwtTokenProvider jwtTokenProvider;
    private final BridgeWorkAuthProperties authProperties;
    private final UserProfileService userProfileService;
    private final UserProfileRepository userProfileRepository;
    private final DiscordNotifierService discordNotifierService;

    public AuthService(AppUserRepository appUserRepository,
                       SocialOAuthService socialOAuthService,
                       SignupSessionStoreService signupSessionStoreService,
                       RefreshTokenStoreService refreshTokenStoreService,
                       JwtTokenProvider jwtTokenProvider,
                       BridgeWorkAuthProperties authProperties,
                       UserProfileService userProfileService,
                       UserProfileRepository userProfileRepository,
                       DiscordNotifierService discordNotifierService) {
        this.appUserRepository = appUserRepository;
        this.socialOAuthService = socialOAuthService;
        this.signupSessionStoreService = signupSessionStoreService;
        this.refreshTokenStoreService = refreshTokenStoreService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authProperties = authProperties;
        this.userProfileService = userProfileService;
        this.userProfileRepository = userProfileRepository;
        this.discordNotifierService = discordNotifierService;
    }

    @Transactional
    public SocialLoginResponseDto socialLogin(SocialLoginRequestDto request) {
        SocialUserProfile socialUserProfile = socialOAuthService.fetchUserProfile(
                request.provider(),
                request.code(),
                request.redirectUri(),
                request.state()
        );

        AppUser user = appUserRepository
                .findByProviderAndProviderUserId(socialUserProfile.provider(), socialUserProfile.providerUserId())
                .orElse(null);

        if (user == null || !user.isSignupCompleted()) {
            String signupToken = signupSessionStoreService.createSession(new SocialSignupSessionData(
                    socialUserProfile.provider(),
                    socialUserProfile.providerUserId(),
                    socialUserProfile.email(),
                    socialUserProfile.name()
            ));

            return new SocialLoginResponseDto(
                    true,
                    signupToken,
                    socialUserProfile.provider(),
                    socialUserProfile.email(),
                    socialUserProfile.name(),
                    null
            );
        }

        TokenPairResponseDto tokenPairResponse = issueAndStoreTokenPair(user);
        return new SocialLoginResponseDto(
                false,
                null,
                user.getProvider(),
                user.getEmail(),
                resolveDefaultProfileName(user.getId()),
                tokenPairResponse
        );
    }

    @Transactional
    public TokenPairResponseDto completeSignup(SignupCompleteRequestDto request) {
        SocialSignupSessionData signupSessionData = signupSessionStoreService.getRequiredSession(request.signupToken());

        String normalizedEmail = normalizeEmail(resolveEmail(signupSessionData.email(), request.email()));

        validateDuplicateIdentity(normalizedEmail, signupSessionData);

        AppUser user = appUserRepository
                .findByProviderAndProviderUserId(signupSessionData.provider(), signupSessionData.providerUserId())
                .orElseGet(AppUser::new);

        user.setProvider(signupSessionData.provider());
        user.setProviderUserId(signupSessionData.providerUserId());
        if (normalizedEmail != null) {
            user.setEmail(normalizedEmail);
        }
        user.setRole(UserRole.USER);
        user.setSignupCompleted(true);

        AppUser savedUser = appUserRepository.save(user);
        // 가입 완료 시 기본 프로필을 생성해 사용자 상태를 일관되게 만든다.
        userProfileService.create(savedUser.getId(), request.profile());

        // 회원가입이 완료되면 세션 토큰은 즉시 제거해 재사용을 차단한다.
        signupSessionStoreService.deleteSession(request.signupToken());
        discordNotifierService.notifySignupCompleted(savedUser.getEmail(), appUserRepository.countRealSignedUpUsers());
        return issueAndStoreTokenPair(savedUser);
    }

    @Transactional
    public TokenPairResponseDto refreshToken(String refreshToken) {
        ParsedJwtToken parsedJwtToken = jwtTokenProvider.parse(refreshToken);

        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(parsedJwtToken.tokenType())) {
            throw new InvalidRefreshTokenException();
        }

        boolean matched = refreshTokenStoreService.matches(parsedJwtToken.userId(), parsedJwtToken.tokenId(), refreshToken);
        if (!matched) {
            throw new InvalidRefreshTokenException();
        }

        AppUser user = appUserRepository.findById(parsedJwtToken.userId())
                .orElseThrow(UserNotFoundException::new);

        refreshTokenStoreService.delete(parsedJwtToken.userId(), parsedJwtToken.tokenId());
        return issueAndStoreTokenPair(user);
    }

    @Transactional
    public void logout(Long userId, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }

        ParsedJwtToken parsedJwtToken;
        try {
            parsedJwtToken = jwtTokenProvider.parse(refreshToken);
        } catch (Exception exception) {
            return;
        }

        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(parsedJwtToken.tokenType())) {
            return;
        }

        if (!userId.equals(parsedJwtToken.userId())) {
            return;
        }

        refreshTokenStoreService.delete(parsedJwtToken.userId(), parsedJwtToken.tokenId());
    }

    public AuthMeResponseDto getMe(Long userId) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        return new AuthMeResponseDto(
                user.getId(),
                user.getProvider(),
                user.getEmail(),
                user.getRole(),
                user.isSignupCompleted()
        );
    }

    private TokenPairResponseDto issueAndStoreTokenPair(AppUser user) {
        JwtTokenPair jwtTokenPair = jwtTokenProvider.issueTokenPair(user.getId(), user.getRole());

        refreshTokenStoreService.save(
                user.getId(),
                jwtTokenPair.refreshTokenId(),
                jwtTokenPair.refreshToken(),
                authProperties.getJwt().getRefreshTokenValidity()
        );

        return new TokenPairResponseDto(
                jwtTokenPair.accessToken(),
                jwtTokenPair.refreshToken(),
                "Bearer",
                jwtTokenPair.accessTokenExpiresAt().withOffsetSameInstant(ZoneOffset.UTC),
                jwtTokenPair.refreshTokenExpiresAt().withOffsetSameInstant(ZoneOffset.UTC)
        );
    }

    private void validateDuplicateIdentity(String normalizedEmail,
                                           SocialSignupSessionData signupSessionData) {
        AppUser existingBySocial = appUserRepository
                .findByProviderAndProviderUserId(signupSessionData.provider(), signupSessionData.providerUserId())
                .orElse(null);

        if (existingBySocial == null) {
            if (normalizedEmail != null && appUserRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateEmailException();
            }
            return;
        }

        String existingEmail = normalizeEmail(existingBySocial.getEmail());
        if (normalizedEmail != null
                && !normalizedEmail.equals(existingEmail)
                && appUserRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveEmail(String socialEmail, String requestEmail) {
        if (StringUtils.hasText(requestEmail)) {
            return requestEmail;
        }
        return socialEmail;
    }

    private String resolveDefaultProfileName(Long userId) {
        return userProfileRepository.findByUser_IdAndIsDefaultTrue(userId)
                .map(profile -> profile.getFullName())
                .orElse(null);
    }
}
