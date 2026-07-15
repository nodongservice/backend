package com.bridgework.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.admin.auth.entity.AdminAccount;
import com.bridgework.admin.auth.repository.AdminAccountRepository;
import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.dto.TokenPairResponseDto;
import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.entity.UserStatus;
import com.bridgework.auth.exception.InvalidRefreshTokenException;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.auth.security.JwtTokenProvider;
import com.bridgework.auth.security.ParsedJwtToken;
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
class TokenSessionServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AdminAccountRepository adminAccountRepository;
    @Mock
    private RefreshTokenStoreService refreshTokenStoreService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private TokenSessionService tokenSessionService;
    private BridgeWorkAuthProperties authProperties;

    @BeforeEach
    void setUp() {
        authProperties = new BridgeWorkAuthProperties();
        authProperties.getJwt().setRefreshTokenValidity(Duration.ofDays(14));
        tokenSessionService = new TokenSessionService(
                appUserRepository,
                adminAccountRepository,
                refreshTokenStoreService,
                jwtTokenProvider,
                authProperties
        );
    }

    @Test
    void issueSeparatesRefreshSessionByRole() {
        JwtTokenPair tokenPair = tokenPair("access", "refresh", "refresh-id");
        when(jwtTokenProvider.issueTokenPair(7L, UserRole.ADMIN)).thenReturn(tokenPair);

        TokenPairResponseDto response = tokenSessionService.issue(7L, UserRole.ADMIN);

        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(refreshTokenStoreService).save(
                eq(7L), eq(UserRole.ADMIN), eq("refresh-id"), eq("session-id"),
                eq("refresh"), eq(Duration.ofDays(14))
        );
    }

    @Test
    void refreshRotatesUserTokenOnlyOnce() {
        AppUser user = activeUser(3L);
        ParsedJwtToken parsedToken = new ParsedJwtToken(
                3L, UserRole.USER, "old-id", JwtTokenProvider.TOKEN_TYPE_REFRESH, "session-id"
        );
        JwtTokenPair nextPair = tokenPair("next-access", "next-refresh", "next-id");
        when(jwtTokenProvider.parse("old-refresh")).thenReturn(parsedToken);
        when(appUserRepository.findByIdAndStatus(3L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.rotateTokenPair(3L, UserRole.USER, "session-id")).thenReturn(nextPair);
        when(refreshTokenStoreService.rotate(
                eq(3L), eq(UserRole.USER), eq("old-id"), eq("old-refresh"),
                eq("next-id"), eq("next-refresh"), eq("session-id"), any(Duration.class)
        )).thenReturn(RefreshTokenStoreService.RotationResult.ROTATED);

        TokenPairResponseDto response = tokenSessionService.refresh("old-refresh");

        assertThat(response.accessToken()).isEqualTo("next-access");
        assertThat(response.refreshToken()).isEqualTo("next-refresh");
    }

    @Test
    void refreshRejectsAlreadyConsumedToken() {
        AppUser user = activeUser(3L);
        when(jwtTokenProvider.parse("replayed-refresh"))
                .thenReturn(new ParsedJwtToken(
                        3L, UserRole.USER, "old-id", JwtTokenProvider.TOKEN_TYPE_REFRESH, "session-id"
                ));
        when(appUserRepository.findByIdAndStatus(3L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.rotateTokenPair(3L, UserRole.USER, "session-id"))
                .thenReturn(tokenPair("next-access", "next-refresh", "next-id"));
        when(refreshTokenStoreService.rotate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(RefreshTokenStoreService.RotationResult.REUSE_DETECTED);

        assertThatThrownBy(() -> tokenSessionService.refresh("replayed-refresh"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshSupportsActiveAdminSession() {
        AdminAccount admin = new AdminAccount();
        ReflectionTestUtils.setField(admin, "id", 9L);
        admin.setActive(true);
        admin.setRole(UserRole.ADMIN);
        when(jwtTokenProvider.parse("admin-refresh"))
                .thenReturn(new ParsedJwtToken(
                        9L, UserRole.ADMIN, "admin-old", JwtTokenProvider.TOKEN_TYPE_REFRESH, "session-id"
                ));
        when(adminAccountRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(jwtTokenProvider.rotateTokenPair(9L, UserRole.ADMIN, "session-id"))
                .thenReturn(tokenPair("admin-access", "admin-next-refresh", "admin-next"));
        when(refreshTokenStoreService.rotate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(RefreshTokenStoreService.RotationResult.ROTATED);

        TokenPairResponseDto response = tokenSessionService.refresh("admin-refresh");

        assertThat(response.accessToken()).isEqualTo("admin-access");
        verify(refreshTokenStoreService).rotate(
                eq(9L), eq(UserRole.ADMIN), eq("admin-old"), eq("admin-refresh"),
                eq("admin-next"), eq("admin-next-refresh"), eq("session-id"), any(Duration.class)
        );
    }

    private AppUser activeUser(Long id) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setSignupCompleted(true);
        return user;
    }

    private JwtTokenPair tokenPair(String accessToken, String refreshToken, String refreshTokenId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new JwtTokenPair(
                accessToken,
                refreshToken,
                refreshTokenId,
                "session-id",
                now.plusMinutes(15),
                now.plusDays(14)
        );
    }
}
