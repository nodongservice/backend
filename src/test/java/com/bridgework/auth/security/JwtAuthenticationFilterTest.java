package com.bridgework.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.admin.auth.repository.AdminAccountRepository;
import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.entity.UserStatus;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.auth.service.RefreshTokenStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AdminAccountRepository adminAccountRepository;
    @Mock
    private RefreshTokenStoreService refreshTokenStoreService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_whenAdminTokenAndAdminActive_thenAuthenticates() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtTokenProvider,
                appUserRepository,
                adminAccountRepository,
                refreshTokenStoreService,
                new ObjectMapper()
        );
        when(jwtTokenProvider.parse("admin-token"))
                .thenReturn(new ParsedJwtToken(99L, UserRole.ADMIN, "token-id", JwtTokenProvider.TOKEN_TYPE_ACCESS, "session-id"));
        when(adminAccountRepository.existsByIdAndActiveTrue(99L)).thenReturn(true);
        when(refreshTokenStoreService.isSessionActive(99L, UserRole.ADMIN, "session-id")).thenReturn(true);

        MockHttpServletRequest request = authorizedRequest("admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(UserPrincipal.class);
        verify(appUserRepository, never()).findByIdAndStatus(99L, UserStatus.ACTIVE);
    }

    @Test
    void doFilterInternal_whenAdminTokenButAdminInactive_thenReturnsUnauthorized() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtTokenProvider,
                appUserRepository,
                adminAccountRepository,
                refreshTokenStoreService,
                new ObjectMapper()
        );
        when(jwtTokenProvider.parse("admin-token"))
                .thenReturn(new ParsedJwtToken(99L, UserRole.ADMIN, "token-id", JwtTokenProvider.TOKEN_TYPE_ACCESS, "session-id"));
        when(adminAccountRepository.existsByIdAndActiveTrue(99L)).thenReturn(false);

        MockHttpServletRequest request = authorizedRequest("admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_JWT");
        assertThat(response.getContentAsString()).contains("비활성화된 계정입니다.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_whenUserTokenAndUserActive_thenAuthenticates() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtTokenProvider,
                appUserRepository,
                adminAccountRepository,
                refreshTokenStoreService,
                new ObjectMapper()
        );
        when(jwtTokenProvider.parse("user-token"))
                .thenReturn(new ParsedJwtToken(7L, UserRole.USER, "token-id", JwtTokenProvider.TOKEN_TYPE_ACCESS, "session-id"));
        when(appUserRepository.findByIdAndStatus(7L, UserStatus.ACTIVE)).thenReturn(Optional.of(new AppUser()));
        when(refreshTokenStoreService.isSessionActive(7L, UserRole.USER, "session-id")).thenReturn(true);

        MockHttpServletRequest request = authorizedRequest("user-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        verify(adminAccountRepository, never()).existsByIdAndActiveTrue(7L);
    }

    @Test
    void doFilterInternal_whenSessionWasRevoked_thenReturnsUnauthorized() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtTokenProvider,
                appUserRepository,
                adminAccountRepository,
                refreshTokenStoreService,
                new ObjectMapper()
        );
        when(jwtTokenProvider.parse("user-token"))
                .thenReturn(new ParsedJwtToken(
                        7L, UserRole.USER, "token-id", JwtTokenProvider.TOKEN_TYPE_ACCESS, "revoked-session"
                ));
        when(appUserRepository.findByIdAndStatus(7L, UserStatus.ACTIVE)).thenReturn(Optional.of(new AppUser()));
        when(refreshTokenStoreService.isSessionActive(7L, UserRole.USER, "revoked-session")).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(authorizedRequest("user-token"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("종료된 로그인 세션입니다.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest authorizedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/admin/sync/public-data");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
