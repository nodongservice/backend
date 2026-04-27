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
import com.bridgework.auth.exception.DuplicatePhoneNumberException;
import com.bridgework.auth.exception.InvalidAuthRequestException;
import com.bridgework.auth.exception.InvalidRefreshTokenException;
import com.bridgework.auth.exception.UserNotFoundException;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.auth.security.JwtTokenProvider;
import com.bridgework.auth.security.ParsedJwtToken;
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

    public AuthService(AppUserRepository appUserRepository,
                       SocialOAuthService socialOAuthService,
                       SignupSessionStoreService signupSessionStoreService,
                       RefreshTokenStoreService refreshTokenStoreService,
                       JwtTokenProvider jwtTokenProvider,
                       BridgeWorkAuthProperties authProperties) {
        this.appUserRepository = appUserRepository;
        this.socialOAuthService = socialOAuthService;
        this.signupSessionStoreService = signupSessionStoreService;
        this.refreshTokenStoreService = refreshTokenStoreService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authProperties = authProperties;
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
                user.getName(),
                tokenPairResponse
        );
    }

    @Transactional
    public TokenPairResponseDto completeSignup(SignupCompleteRequestDto request) {
        SocialSignupSessionData signupSessionData = signupSessionStoreService.getRequiredSession(request.signupToken());

        String normalizedPhoneNumber = normalizePhoneNumber(request.phoneNumber());
        String normalizedEmail = normalizeEmail(resolveEmail(signupSessionData.email(), request.email()));

        validateDuplicateIdentity(normalizedPhoneNumber, normalizedEmail, signupSessionData);

        AppUser user = appUserRepository
                .findByProviderAndProviderUserId(signupSessionData.provider(), signupSessionData.providerUserId())
                .orElseGet(AppUser::new);

        user.setProvider(signupSessionData.provider());
        user.setProviderUserId(signupSessionData.providerUserId());
        if (normalizedEmail != null) {
            user.setEmail(normalizedEmail);
        }
        user.setName(request.name().trim());
        user.setAge(request.age());
        user.setGender(request.gender());
        user.setLocation(request.location().trim());
        user.setPhoneNumber(normalizedPhoneNumber);
        user.setRole(UserRole.USER);
        user.setSignupCompleted(true);

        AppUser savedUser = appUserRepository.save(user);

        // 회원가입이 완료되면 세션 토큰은 즉시 제거해 재사용을 차단한다.
        signupSessionStoreService.deleteSession(request.signupToken());
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
                user.getName(),
                user.getAge(),
                user.getGender(),
                user.getLocation(),
                user.getPhoneNumber(),
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

    private void validateDuplicateIdentity(String normalizedPhoneNumber,
                                           String normalizedEmail,
                                           SocialSignupSessionData signupSessionData) {
        AppUser existingBySocial = appUserRepository
                .findByProviderAndProviderUserId(signupSessionData.provider(), signupSessionData.providerUserId())
                .orElse(null);

        if (existingBySocial == null) {
            if (appUserRepository.existsByPhoneNumber(normalizedPhoneNumber)) {
                throw new DuplicatePhoneNumberException();
            }
            if (normalizedEmail != null && appUserRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateEmailException();
            }
            return;
        }

        String existingPhoneNumber = existingBySocial.getPhoneNumber();
        if ((existingPhoneNumber == null || !existingPhoneNumber.equals(normalizedPhoneNumber))
                && appUserRepository.existsByPhoneNumber(normalizedPhoneNumber)) {
            throw new DuplicatePhoneNumberException();
        }

        String existingEmail = existingBySocial.getEmail();
        if (normalizedEmail != null
                && existingEmail != null
                && !existingEmail.equals(normalizedEmail)
                && appUserRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            throw new InvalidAuthRequestException("전화번호는 필수입니다.");
        }

        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        if (normalized.length() < 8 || normalized.length() > 20) {
            throw new InvalidAuthRequestException("전화번호 형식이 올바르지 않습니다.");
        }

        return normalized;
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
}
