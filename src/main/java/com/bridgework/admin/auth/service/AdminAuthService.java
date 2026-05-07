package com.bridgework.admin.auth.service;

import com.bridgework.admin.auth.dto.AdminLoginRequestDto;
import com.bridgework.admin.auth.dto.AdminLoginResponseDto;
import com.bridgework.admin.auth.entity.AdminAccount;
import com.bridgework.admin.auth.exception.InvalidAdminCredentialsException;
import com.bridgework.admin.auth.repository.AdminAccountRepository;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.security.JwtTokenProvider;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminAuthService {

    private static final int ADMIN_MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration ADMIN_LOGIN_LOCK_DURATION = Duration.ofMinutes(15);

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AdminAuthService(AdminAccountRepository adminAccountRepository,
                            PasswordEncoder passwordEncoder,
                            JwtTokenProvider jwtTokenProvider) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AdminLoginResponseDto login(AdminLoginRequestDto request) {
        String normalizedLoginId = normalizeLoginId(request.loginId());
        if (!StringUtils.hasText(normalizedLoginId)) {
            throw new InvalidAdminCredentialsException();
        }

        AdminAccount adminAccount = adminAccountRepository.findForUpdateByLoginId(normalizedLoginId)
                .orElseThrow(InvalidAdminCredentialsException::new);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!adminAccount.isActive()
                || adminAccount.getRole() != UserRole.ADMIN
                || adminAccount.isLockedAt(now)) {
            throw new InvalidAdminCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), adminAccount.getPasswordHash())) {
            adminAccount.registerFailedLogin(now, ADMIN_MAX_FAILED_LOGIN_ATTEMPTS, ADMIN_LOGIN_LOCK_DURATION);
            throw new InvalidAdminCredentialsException();
        }

        adminAccount.clearLoginFailureState(now);
        JwtTokenProvider.IssuedAccessToken issuedAccessToken =
                jwtTokenProvider.issueAccessToken(adminAccount.getId(), adminAccount.getRole());

        return new AdminLoginResponseDto(
                issuedAccessToken.token(),
                "Bearer",
                issuedAccessToken.expiresAt().withOffsetSameInstant(ZoneOffset.UTC)
        );
    }

    private String normalizeLoginId(String loginId) {
        if (!StringUtils.hasText(loginId)) {
            return null;
        }
        return loginId.trim().toLowerCase(Locale.ROOT);
    }
}
