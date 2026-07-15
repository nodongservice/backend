package com.bridgework.auth.service;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RefreshTokenCookieService {

    public static final String COOKIE_PATH = "/api/v1/auth";

    private final BridgeWorkAuthProperties authProperties;

    public RefreshTokenCookieService(BridgeWorkAuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return Optional.empty();
        }
        String cookieName = authProperties.getRefreshCookie().getName();
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    public String createHeader(String refreshToken) {
        return build(refreshToken, authProperties.getJwt().getRefreshTokenValidity()).toString();
    }

    public String clearHeader() {
        return build("", Duration.ZERO).toString();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        BridgeWorkAuthProperties.RefreshCookie cookie = authProperties.getRefreshCookie();
        return ResponseCookie.from(cookie.getName(), value)
                .httpOnly(true)
                .secure(cookie.isSecure())
                .sameSite(cookie.getSameSite())
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
