package com.bridgework.auth.service;

import com.bridgework.admin.auth.entity.AdminAccount;
import com.bridgework.admin.auth.repository.AdminAccountRepository;
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
import com.bridgework.auth.exception.SignupCompletionInProgressException;
import com.bridgework.auth.exception.UserNotFoundException;
import com.bridgework.auth.exception.WithdrawalNotPendingException;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.common.notification.DiscordNotifierService;
import com.bridgework.profile.entity.UserProfile;
import com.bridgework.profile.repository.UserProfileRepository;
import com.bridgework.profile.service.UserProfileCommandFacade;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository appUserRepository;
    private final SocialOAuthService socialOAuthService;
    private final SignupSessionStoreService signupSessionStoreService;
    private final TokenSessionService tokenSessionService;
    private final BridgeWorkAuthProperties authProperties;
    private final SignupCompletionPersistenceService signupCompletionPersistenceService;
    private final UserProfileCommandFacade userProfileCommandFacade;
    private final UserProfileRepository userProfileRepository;
    private final DiscordNotifierService discordNotifierService;
    private final WithdrawalCancelTokenStoreService withdrawalCancelTokenStoreService;
    private final AdminAccountRepository adminAccountRepository;

    public AuthService(AppUserRepository appUserRepository,
                       SocialOAuthService socialOAuthService,
                       SignupSessionStoreService signupSessionStoreService,
                       TokenSessionService tokenSessionService,
                       BridgeWorkAuthProperties authProperties,
                       SignupCompletionPersistenceService signupCompletionPersistenceService,
                       UserProfileCommandFacade userProfileCommandFacade,
                       UserProfileRepository userProfileRepository,
                       DiscordNotifierService discordNotifierService,
                       WithdrawalCancelTokenStoreService withdrawalCancelTokenStoreService,
                       AdminAccountRepository adminAccountRepository) {
        this.appUserRepository = appUserRepository;
        this.socialOAuthService = socialOAuthService;
        this.signupSessionStoreService = signupSessionStoreService;
        this.tokenSessionService = tokenSessionService;
        this.authProperties = authProperties;
        this.signupCompletionPersistenceService = signupCompletionPersistenceService;
        this.userProfileCommandFacade = userProfileCommandFacade;
        this.userProfileRepository = userProfileRepository;
        this.discordNotifierService = discordNotifierService;
        this.withdrawalCancelTokenStoreService = withdrawalCancelTokenStoreService;
        this.adminAccountRepository = adminAccountRepository;
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

    public TokenPairResponseDto completeSignup(SignupCompleteRequestDto request) {
        String lockOwner = signupSessionStoreService.tryAcquireCompletionLock(request.signupToken())
                .orElseThrow(SignupCompletionInProgressException::new);
        try {
            SocialSignupSessionData signupSessionData = signupSessionStoreService.getRequiredSession(request.signupToken());
            var homeGeoPoint = userProfileCommandFacade.prepareHomeCoordinates(request.profile().detailAddress());
            CompletedSignupUser completedUser = signupCompletionPersistenceService.complete(request, signupSessionData, homeGeoPoint);

            // DB 커밋이 성공한 뒤에만 Redis 토큰과 가입 세션을 변경한다.
            TokenPairResponseDto tokenPair = issueAndStoreTokenPair(completedUser.userId(), completedUser.role());
            signupSessionStoreService.deleteSession(request.signupToken());
            notifySignupCompletedSafely(completedUser);
            return tokenPair;
        } finally {
            releaseSignupCompletionLockSafely(request.signupToken(), lockOwner);
        }
    }

    @Transactional
    public TokenPairResponseDto refreshToken(String refreshToken) {
        return tokenSessionService.refresh(refreshToken);
    }

    public void logout(String refreshToken) {
        tokenSessionService.revoke(refreshToken);
    }

    public AuthMeResponseDto getMe(Long userId, UserRole role) {
        if (role == UserRole.ADMIN) {
            AdminAccount adminAccount = adminAccountRepository.findById(userId)
                    .filter(AdminAccount::isActive)
                    .orElseThrow(UserNotFoundException::new);

            return new AuthMeResponseDto(
                    adminAccount.getId(),
                    null,
                    adminAccount.getLoginId() + "@admin.bridgework.local",
                    adminAccount.getRole(),
                    true
            );
        }

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

        tokenSessionService.revokeAll(userId, UserRole.USER);
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
        tokenSessionService.revokeAll(user.getId(), UserRole.USER);
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
        return issueAndStoreTokenPair(user.getId(), user.getRole());
    }

    private TokenPairResponseDto issueAndStoreTokenPair(Long userId, UserRole role) {
        return tokenSessionService.issue(userId, role);
    }

    private void notifySignupCompletedSafely(CompletedSignupUser completedUser) {
        try {
            discordNotifierService.notifySignupCompleted(
                    completedUser.email(),
                    appUserRepository.countRealSignedUpUsers()
            );
        } catch (Exception exception) {
            log.warn("회원가입 완료 알림 전송 준비 실패: {}", exception.getClass().getSimpleName());
        }
    }

    private void releaseSignupCompletionLockSafely(String signupToken, String lockOwner) {
        try {
            signupSessionStoreService.releaseCompletionLock(signupToken, lockOwner);
        } catch (Exception exception) {
            log.warn("회원가입 완료 잠금 해제 실패: {}", exception.getClass().getSimpleName());
        }
    }

    private String resolveDefaultProfileName(Long userId) {
        return userProfileRepository.findByUser_IdAndIsDefaultTrue(userId)
                .map(profile -> StringUtils.hasText(profile.getProfileName()) ? profile.getProfileName() : profile.getFullName())
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
        tokenSessionService.revokeAll(userId, UserRole.USER);
    }

    private String buildAnonymizedProfileEmail(Long profileId, Long userId) {
        String safeProfileId = profileId == null ? "0" : profileId.toString();
        return "deleted-profile-" + userId + "-" + safeProfileId + "@bridgework.local";
    }

    private String buildAnonymizedUserEmail(Long userId) {
        return "deleted-user-" + userId + "-" + UUID.randomUUID().toString().replace("-", "") + "@bridgework.local";
    }
}
