package com.bridgework.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RefreshTokenCookieServiceTest {

    private RefreshTokenCookieService cookieService;

    @BeforeEach
    void setUp() {
        BridgeWorkAuthProperties properties = new BridgeWorkAuthProperties();
        properties.getRefreshCookie().setSecure(true);
        properties.getRefreshCookie().setSameSite("Lax");
        cookieService = new RefreshTokenCookieService(properties);
    }

    @Test
    void createHeaderUsesHardenedCookieAttributes() {
        String header = cookieService.createHeader("refresh-token");

        assertThat(header).contains("bridgework_refresh=refresh-token");
        assertThat(header).contains("Path=/api/v1/auth");
        assertThat(header).contains("Secure");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("SameSite=Lax");
    }

    @Test
    void readFindsRefreshCookieAndClearExpiresIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "value"), new Cookie("bridgework_refresh", "refresh-token"));

        assertThat(cookieService.read(request)).contains("refresh-token");
        assertThat(cookieService.clearHeader()).contains("Max-Age=0");
    }
}
