package com.bridgework.auth.service;

import com.bridgework.auth.dto.SignupCompleteRequestDto;
import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.entity.UserStatus;
import com.bridgework.auth.exception.DuplicateEmailException;
import com.bridgework.auth.exception.InvalidAuthRequestException;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.profile.repository.UserProfileRepository;
import com.bridgework.profile.service.UserProfileService;
import com.bridgework.sync.normalized.NormalizedGeoPoint;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SignupCompletionPersistenceService {

    private final AppUserRepository appUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileService userProfileService;

    public SignupCompletionPersistenceService(AppUserRepository appUserRepository,
                                              UserProfileRepository userProfileRepository,
                                              UserProfileService userProfileService) {
        this.appUserRepository = appUserRepository;
        this.userProfileRepository = userProfileRepository;
        this.userProfileService = userProfileService;
    }

    @Transactional
    public CompletedSignupUser complete(SignupCompleteRequestDto request,
                                        SocialSignupSessionData signupSessionData,
                                        Optional<NormalizedGeoPoint> homeGeoPoint) {
        String normalizedEmail = normalizeEmail(signupSessionData.email());
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new InvalidAuthRequestException("소셜 계정 이메일 정보를 확인할 수 없습니다. 이메일 제공 동의 후 다시 시도해 주세요.");
        }

        AppUser existingBySocial = appUserRepository
                .findByProviderAndProviderUserId(signupSessionData.provider(), signupSessionData.providerUserId())
                .orElse(null);

        if (isAlreadyCompleted(existingBySocial, normalizedEmail)) {
            return toCompletedUser(existingBySocial);
        }

        validateDuplicateIdentity(normalizedEmail, existingBySocial);

        AppUser user = existingBySocial == null ? new AppUser() : existingBySocial;
        user.setProvider(signupSessionData.provider());
        user.setProviderUserId(signupSessionData.providerUserId());
        user.setEmail(normalizedEmail);
        user.setRole(UserRole.USER);
        user.setSignupCompleted(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setWithdrawalRequestedAt(null);
        user.setDeletedAt(null);

        AppUser savedUser = appUserRepository.saveAndFlush(user);
        userProfileService.createWithResolvedHomeCoordinates(savedUser.getId(), request.profile(), homeGeoPoint);
        return toCompletedUser(savedUser);
    }

    private boolean isAlreadyCompleted(AppUser user, String normalizedEmail) {
        if (user == null || !user.isSignupCompleted() || !UserStatus.ACTIVE.equals(user.getStatus())) {
            return false;
        }
        if (!normalizedEmail.equals(normalizeEmail(user.getEmail()))) {
            throw new DuplicateEmailException();
        }
        if (userProfileRepository.countByUser_Id(user.getId()) == 0) {
            return false;
        }
        return true;
    }

    private void validateDuplicateIdentity(String normalizedEmail, AppUser existingBySocial) {
        if (existingBySocial == null) {
            if (appUserRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateEmailException();
            }
            return;
        }
        if (!normalizedEmail.equals(normalizeEmail(existingBySocial.getEmail()))
                && appUserRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }
    }

    private CompletedSignupUser toCompletedUser(AppUser user) {
        return new CompletedSignupUser(user.getId(), user.getEmail(), user.getRole());
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
