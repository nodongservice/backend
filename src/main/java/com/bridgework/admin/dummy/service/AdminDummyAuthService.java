package com.bridgework.admin.dummy.service;

import com.bridgework.admin.auth.entity.AdminAccount;
import com.bridgework.admin.auth.repository.AdminAccountRepository;
import com.bridgework.admin.dummy.dto.AdminDummyCaseResponseDto;
import com.bridgework.admin.dummy.dto.AdminDummyLoginRequestDto;
import com.bridgework.admin.dummy.dto.AdminDummyLoginResponseDto;
import com.bridgework.admin.dummy.dto.AdminDummyProfileOptionDto;
import com.bridgework.admin.dummy.entity.AdminDummyLoginAudit;
import com.bridgework.admin.dummy.entity.AdminDummyProfile;
import com.bridgework.admin.dummy.entity.AdminDummyUser;
import com.bridgework.admin.dummy.exception.AdminDummyAuthException;
import com.bridgework.admin.dummy.repository.AdminDummyLoginAuditRepository;
import com.bridgework.admin.dummy.repository.AdminDummyProfileRepository;
import com.bridgework.admin.dummy.repository.AdminDummyUserRepository;
import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.exception.AuthDomainException;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.security.JwtTokenProvider;
import com.bridgework.auth.service.JwtTokenPair;
import com.bridgework.auth.service.RefreshTokenStoreService;
import com.bridgework.audit.service.PersonalDataAccessAuditService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminDummyAuthService {

    private final AdminDummyUserRepository adminDummyUserRepository;
    private final AdminDummyProfileRepository adminDummyProfileRepository;
    private final AdminDummyLoginAuditRepository adminDummyLoginAuditRepository;
    private final AdminAccountRepository adminAccountRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStoreService refreshTokenStoreService;
    private final BridgeWorkAuthProperties authProperties;
    private final PersonalDataAccessAuditService personalDataAccessAuditService;

    public AdminDummyAuthService(AdminDummyUserRepository adminDummyUserRepository,
                                 AdminDummyProfileRepository adminDummyProfileRepository,
                                 AdminDummyLoginAuditRepository adminDummyLoginAuditRepository,
                                 AdminAccountRepository adminAccountRepository,
                                 JwtTokenProvider jwtTokenProvider,
                                 RefreshTokenStoreService refreshTokenStoreService,
                                 BridgeWorkAuthProperties authProperties,
                                 PersonalDataAccessAuditService personalDataAccessAuditService) {
        this.adminDummyUserRepository = adminDummyUserRepository;
        this.adminDummyProfileRepository = adminDummyProfileRepository;
        this.adminDummyLoginAuditRepository = adminDummyLoginAuditRepository;
        this.adminAccountRepository = adminAccountRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenStoreService = refreshTokenStoreService;
        this.authProperties = authProperties;
        this.personalDataAccessAuditService = personalDataAccessAuditService;
    }

    @Transactional(readOnly = true)
    public List<AdminDummyCaseResponseDto> getActiveCases() {
        return adminDummyUserRepository.findByIsActiveTrueOrderByIdAsc().stream()
                .map(dummyUser -> new AdminDummyCaseResponseDto(
                        dummyUser.getDummyKey(),
                        dummyUser.getDisplayName(),
                        dummyUser.getScenarioSummary(),
                        toProfileOptions(dummyUser)
                ))
                .toList();
    }

    @Transactional
    public AdminDummyLoginResponseDto loginAsDummyUser(Long adminUserId,
                                                       String requestIp,
                                                       AdminDummyLoginRequestDto request) {
        AdminAccount adminAccount = adminAccountRepository.findById(adminUserId)
                .filter(AdminAccount::isActive)
                .orElseThrow(() -> new AuthDomainException("ADMIN_NOT_FOUND", HttpStatus.NOT_FOUND, "관리자 계정을 찾을 수 없습니다."));
        if (!adminAccount.isSensitiveProfileAccessEnabled()) {
            personalDataAccessAuditService.record(adminUserId, "DUMMY_USER_LOGIN", null, null, requestIp, "DENIED", "민감 프로필 접근 권한 없음");
            throw new AuthDomainException("SENSITIVE_PROFILE_ACCESS_DENIED", HttpStatus.FORBIDDEN, "민감 프로필 접근 권한이 없습니다.");
        }

        String dummyKey = normalizeDummyKey(request.dummyKey());
        if (!StringUtils.hasText(dummyKey)) {
            throw new AdminDummyAuthException(
                    "DUMMY_KEY_REQUIRED",
                    HttpStatus.BAD_REQUEST,
                    "dummyKey는 필수입니다."
            );
        }

        AdminDummyUser dummyUser = adminDummyUserRepository.findByDummyKeyAndIsActiveTrue(dummyKey)
                .orElseThrow(() -> new AdminDummyAuthException(
                        "DUMMY_USER_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "활성 더미 사용자를 찾을 수 없습니다. dummyKey=" + dummyKey
                ));

        if (dummyUser.getAppUser().getRole() != UserRole.USER || !dummyUser.getAppUser().isSignupCompleted()) {
            throw new AdminDummyAuthException(
                    "DUMMY_USER_INVALID_STATE",
                    HttpStatus.CONFLICT,
                    "더미 사용자 상태가 유효하지 않습니다."
            );
        }

        List<AdminDummyProfileOptionDto> profiles = toProfileOptions(dummyUser);
        if (profiles.isEmpty()) {
            throw new AdminDummyAuthException(
                    "DUMMY_PROFILE_NOT_FOUND",
                    HttpStatus.CONFLICT,
                    "더미 사용자에 연결된 프로필이 없습니다."
            );
        }

        JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(
                dummyUser.getAppUser().getId(),
                UserRole.USER
        );
        refreshTokenStoreService.save(
                dummyUser.getAppUser().getId(),
                tokenPair.refreshTokenId(),
                tokenPair.refreshToken(),
                authProperties.getJwt().getRefreshTokenValidity()
        );

        AdminDummyLoginAudit loginAudit = new AdminDummyLoginAudit();
        loginAudit.setAdminUserId(adminUserId);
        loginAudit.setDummyUser(dummyUser);
        loginAudit.setIssuedUser(dummyUser.getAppUser());
        loginAudit.setRequestIp(trimToNull(requestIp));
        loginAudit.setIssuedAt(OffsetDateTime.now(ZoneOffset.UTC));
        adminDummyLoginAuditRepository.save(loginAudit);
        Long defaultProfileId = profiles.stream()
                .filter(AdminDummyProfileOptionDto::isDefault)
                .map(AdminDummyProfileOptionDto::profileId)
                .findFirst()
                .orElse(profiles.get(0).profileId());
        personalDataAccessAuditService.record(
                adminUserId,
                "DUMMY_USER_LOGIN",
                dummyUser.getAppUser().getId(),
                defaultProfileId,
                trimToNull(requestIp),
                "GRANTED",
                "관리자 더미 사용자 대리 로그인"
        );

        return new AdminDummyLoginResponseDto(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                "Bearer",
                tokenPair.accessTokenExpiresAt().withOffsetSameInstant(ZoneOffset.UTC),
                tokenPair.refreshTokenExpiresAt().withOffsetSameInstant(ZoneOffset.UTC),
                dummyUser.getAppUser().getId(),
                dummyUser.getDummyKey(),
                profiles
        );
    }

    private List<AdminDummyProfileOptionDto> toProfileOptions(AdminDummyUser dummyUser) {
        List<AdminDummyProfile> dummyProfiles = adminDummyProfileRepository
                .findByDummyUser_IdOrderBySortOrderAscIdAsc(dummyUser.getId());

        return dummyProfiles.stream()
                .filter(dummyProfile -> belongsToDummyUser(dummyUser, dummyProfile))
                .map(dummyProfile -> new AdminDummyProfileOptionDto(
                        dummyProfile.getProfile().getId(),
                        dummyProfile.getProfileKey(),
                        dummyProfile.getProfileLabel(),
                        dummyProfile.getScenarioSummary(),
                        dummyProfile.getProfile().isDefault()
                ))
                .toList();
    }

    private boolean belongsToDummyUser(AdminDummyUser dummyUser, AdminDummyProfile dummyProfile) {
        return dummyProfile.getProfile().getUser().getId().equals(dummyUser.getAppUser().getId());
    }

    private String normalizeDummyKey(String dummyKey) {
        if (!StringUtils.hasText(dummyKey)) {
            return null;
        }
        return dummyKey.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
