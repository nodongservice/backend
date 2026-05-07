package com.bridgework.admin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.admin.auth.dto.AdminLoginRequestDto;
import com.bridgework.admin.auth.dto.AdminLoginResponseDto;
import com.bridgework.admin.auth.entity.AdminAccount;
import com.bridgework.admin.auth.exception.AdminAccountLockedException;
import com.bridgework.admin.auth.exception.InvalidAdminCredentialsException;
import com.bridgework.admin.auth.repository.AdminAccountRepository;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.security.JwtTokenProvider;
import com.bridgework.common.notification.DiscordNotifierService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private AdminAccountRepository adminAccountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private DiscordNotifierService discordNotifierService;

    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        adminAuthService = new AdminAuthService(
                adminAccountRepository,
                passwordEncoder,
                jwtTokenProvider,
                discordNotifierService
        );
    }

    @Test
    void login_whenCredentialsAreValid_thenReturnsAccessToken() {
        AdminAccount adminAccount = createAdmin(99L, "admin01", "$2a$12$dummy.hash.value");
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(15);

        when(adminAccountRepository.findForUpdateByLoginId("admin01")).thenReturn(Optional.of(adminAccount));
        when(passwordEncoder.matches("plain-password", "$2a$12$dummy.hash.value")).thenReturn(true);
        when(jwtTokenProvider.issueAccessToken(99L, UserRole.ADMIN))
                .thenReturn(new JwtTokenProvider.IssuedAccessToken("admin-access-token", expiresAt));

        AdminLoginResponseDto response = adminAuthService.login(new AdminLoginRequestDto("admin01", "plain-password"));

        assertThat(response.accessToken()).isEqualTo("admin-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessTokenExpiresAt()).isEqualTo(expiresAt.withOffsetSameInstant(ZoneOffset.UTC));
        verify(discordNotifierService, never()).notifyAdminAccountLocked(any(), any(), any());
    }

    @Test
    void login_whenAdminAccountNotFound_thenThrows() {
        when(adminAccountRepository.findForUpdateByLoginId("missing-admin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequestDto("missing-admin", "plain-password")))
                .isInstanceOf(InvalidAdminCredentialsException.class);
    }

    @Test
    void login_whenPasswordMismatch_thenIncreasesFailedCount() {
        AdminAccount adminAccount = createAdmin(100L, "admin01", "$2a$12$dummy.hash.value");

        when(adminAccountRepository.findForUpdateByLoginId("admin01")).thenReturn(Optional.of(adminAccount));
        when(passwordEncoder.matches("wrong-password", "$2a$12$dummy.hash.value")).thenReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequestDto("admin01", "wrong-password")))
                .isInstanceOf(InvalidAdminCredentialsException.class);
        assertThat(adminAccount.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void login_whenAccountAlreadyLocked_thenThrowsLockedException() {
        AdminAccount adminAccount = createAdmin(100L, "admin01", "$2a$12$dummy.hash.value");
        ReflectionTestUtils.setField(adminAccount, "lockedUntil", OffsetDateTime.now().plusMinutes(10));

        when(adminAccountRepository.findForUpdateByLoginId("admin01")).thenReturn(Optional.of(adminAccount));

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequestDto("admin01", "plain-password")))
                .isInstanceOf(AdminAccountLockedException.class);
        verify(discordNotifierService).notifyAdminAccountLocked(eq("admin01"), any(), eq("잠금 상태에서 로그인 시도"));
    }

    @Test
    void login_whenFailedAttemptsReachThreshold_thenThrowsLockedException() {
        AdminAccount adminAccount = createAdmin(100L, "admin01", "$2a$12$dummy.hash.value");
        ReflectionTestUtils.setField(adminAccount, "failedLoginAttempts", 4);

        when(adminAccountRepository.findForUpdateByLoginId("admin01")).thenReturn(Optional.of(adminAccount));
        when(passwordEncoder.matches("wrong-password", "$2a$12$dummy.hash.value")).thenReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequestDto("admin01", "wrong-password")))
                .isInstanceOf(AdminAccountLockedException.class);
        assertThat(adminAccount.getLockedUntil()).isNotNull();
        verify(discordNotifierService).notifyAdminAccountLocked(eq("admin01"), any(), eq("로그인 실패 누적으로 잠금 전환"));
    }

    private AdminAccount createAdmin(Long id, String loginId, String passwordHash) {
        AdminAccount adminAccount = new AdminAccount();
        ReflectionTestUtils.setField(adminAccount, "id", id);
        adminAccount.setLoginId(loginId);
        adminAccount.setPasswordHash(passwordHash);
        adminAccount.setRole(UserRole.ADMIN);
        adminAccount.setActive(true);
        return adminAccount;
    }
}
