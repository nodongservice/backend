package com.bridgework.auth.service;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.dto.AuthMeResponseDto;
import com.bridgework.auth.dto.SignupCompleteRequestDto;
import com.bridgework.auth.dto.SocialLoginAccountStatus;
import com.bridgework.auth.dto.SocialLoginRequestDto;
import com.bridgework.auth.dto.SocialLoginResponseDto;
import com.bridgework.auth.dto.TokenPairResponseDto;
import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.entity.UserStatus;
import com.bridgework.auth.exception.DuplicateEmailException;
import com.bridgework.auth.exception.InvalidRefreshTokenException;
import com.bridgework.auth.exception.InvalidAuthRequestException;
import com.bridgework.auth.exception.UserNotFoundException;
import com.bridgework.auth.exception.WithdrawalNotPendingException;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.auth.security.JwtTokenProvider;
import com.bridgework.auth.security.ParsedJwtToken;
import com.bridgework.common.notification.DiscordNotifierService;
import com.bridgework.profile.entity.UserProfile;
import com.bridgework.profile.repository.UserProfileRepository;
import com.bridgework.profile.service.UserProfileService;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
    private final WithdrawalCancelTokenStoreService withdrawalCancelTokenStoreService;

    public AuthService(AppUserRepository appUserRepository,
                       SocialOAuthService socialOAuthService,
                       SignupSessionStoreService signupSessionStoreService,
                       RefreshTokenStoreService refreshTokenStoreService,
                       JwtTokenProvider jwtTokenProvider,
                       BridgeWorkAuthProperties authProperties,
                       UserProfileService userProfileService,
                       UserProfileRepository userProfileRepository,
                       DiscordNotifierService discordNotifierService,
                       WithdrawalCancelTokenStoreService withdrawalCancelTokenStoreService) {
        this.appUserRepository = appUserRepository;
        this.socialOAuthService = socialOAuthService;
        this.signupSessionStoreService = signupSessionStoreService;
        this.refreshTokenStoreService = refreshTokenStoreService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authProperties = authProperties;
        this.userProfileService = userProfileService;
        this.userProfileRepository = userProfileRepository;
        this.discordNotifierService = discordNotifierService;
        this.withdrawalCancelTokenStoreService = withdrawalCancelTokenStoreService;
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

        if (user != null && UserStatus.PENDING_DELETION.equals(user.getStatus())) {
            OffsetDateTime deadlineAt = resolveWithdrawalDeadlineAt(user);
            if (deadlineAt != null && !OffsetDateTime.now().isBefore(deadlineAt)) {
                finalizeUserDeletion(user, OffsetDateTime.now());
                user = null;
            } else {
                String cancelToken = withdrawalCancelTokenStoreService.createToken(
                        user.getId(),
                        authProperties.getWithdrawalGracePeriod()
                );
                return new SocialLoginResponseDto(
                        false,
                        null,
                        user.getProvider(),
                        user.getEmail(),
                        resolveDefaultProfileName(user.getId()),
                        SocialLoginAccountStatus.PENDING_DELETION,
                        deadlineAt,
                        cancelToken,
                        null
                );
            }
        }

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
                    SocialLoginAccountStatus.SIGNUP_REQUIRED,
                    null,
                    null,
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
                SocialLoginAccountStatus.ACTIVE,
                null,
                null,
                tokenPairResponse
        );
    }

    @Transactional
    public TokenPairResponseDto completeSignup(SignupCompleteRequestDto request) {
        SocialSignupSessionData signupSessionData = signupSessionStoreService.getRequiredSession(request.signupToken());

        String normalizedEmail = normalizeEmail(signupSessionData.email());
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new InvalidAuthRequestException("소셜 계정 이메일 정보를 확인할 수 없습니다. 이메일 제공 동의 후 다시 시도해 주세요.");
        }

        validateDuplicateIdentity(normalizedEmail, signupSessionData);

        AppUser user = appUserRepository
                .findByProviderAndProviderUserId(signupSessionData.provider(), signupSessionData.providerUserId())
                .orElseGet(AppUser::new);

        user.setProvider(signupSessionData.provider());
        user.setProviderUserId(signupSessionData.providerUserId());
        user.setEmail(normalizedEmail);
        user.setRole(UserRole.USER);
        user.setSignupCompleted(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setWithdrawalRequestedAt(null);
        user.setDeletedAt(null);

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

        AppUser user = appUserRepository.findByIdAndStatus(parsedJwtToken.userId(), UserStatus.ACTIVE)
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
        AppUser user = appUserRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);

        return new AuthMeResponseDto(
                user.getId(),
                user.getProvider(),
                user.getEmail(),
                user.getRole(),
                user.isSignupCompleted()
        );
    }

    @Transactional
    public void withdraw(Long userId) {
        AppUser user = appUserRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);

        user.setStatus(UserStatus.PENDING_DELETION);
        user.setWithdrawalRequestedAt(OffsetDateTime.now());

        refreshTokenStoreService.deleteAllByUserId(userId);
    }

    @Transactional
    public TokenPairResponseDto cancelWithdrawal(String withdrawalCancelToken) {
        Long userId = withdrawalCancelTokenStoreService.getRequiredUserId(withdrawalCancelToken);
        AppUser user = appUserRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        if (!UserStatus.PENDING_DELETION.equals(user.getStatus())) {
            throw new WithdrawalNotPendingException();
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setWithdrawalRequestedAt(null);
        user.setDeletedAt(null);
        withdrawalCancelTokenStoreService.deleteToken(withdrawalCancelToken);
        return issueAndStoreTokenPair(user);
    }

    @Transactional
    public int finalizeDueWithdrawals(OffsetDateTime now) {
        OffsetDateTime referenceTime = now == null ? OffsetDateTime.now() : now;
        OffsetDateTime expirationCutoff = referenceTime.minus(authProperties.getWithdrawalGracePeriod());
        List<AppUser> targets = appUserRepository.findAllByStatusAndWithdrawalRequestedAtBefore(
                UserStatus.PENDING_DELETION,
                expirationCutoff
        );

        for (AppUser target : targets) {
            finalizeUserDeletion(target, referenceTime);
        }
        return targets.size();
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
            if (appUserRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateEmailException();
            }
            return;
        }

        String existingEmail = normalizeEmail(existingBySocial.getEmail());
        if (!normalizedEmail.equals(existingEmail)
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

    private String resolveDefaultProfileName(Long userId) {
        return userProfileRepository.findByUser_IdAndIsDefaultTrue(userId)
                .map(profile -> profile.getFullName())
                .orElse(null);
    }

    private OffsetDateTime resolveWithdrawalDeadlineAt(AppUser user) {
        if (user.getWithdrawalRequestedAt() == null) {
            return null;
        }
        return user.getWithdrawalRequestedAt().plus(authProperties.getWithdrawalGracePeriod());
    }

    private void finalizeUserDeletion(AppUser user, OffsetDateTime deletedAt) {
        Long userId = user.getId();
        String deletedIdentity = "deleted:" + userId + ":" + UUID.randomUUID().toString().replace("-", "");
        user.setProviderUserId(deletedIdentity);
        user.setEmail(buildAnonymizedUserEmail(userId));
        user.setSignupCompleted(false);
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(deletedAt);

        List<UserProfile> profiles = userProfileRepository.findByUser_IdOrderByIsDefaultDescUpdatedAtDesc(userId);
        for (UserProfile profile : profiles) {
            profile.anonymizeForWithdrawal(buildAnonymizedProfileEmail(profile.getId(), userId));
        }
        userProfileRepository.saveAll(profiles);
        refreshTokenStoreService.deleteAllByUserId(userId);
    }

    private String buildAnonymizedProfileEmail(Long profileId, Long userId) {
        String safeProfileId = profileId == null ? "0" : profileId.toString();
        return "deleted-profile-" + userId + "-" + safeProfileId + "@bridgework.local";
    }

    private String buildAnonymizedUserEmail(Long userId) {
        return "deleted-user-" + userId + "-" + UUID.randomUUID().toString().replace("-", "") + "@bridgework.local";
    }
}
