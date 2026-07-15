package com.bridgework.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshCookieOriginFilterTest {

    private RefreshCookieOriginFilter filter;

    @BeforeEach
    void setUp() {
        BridgeWorkAuthProperties properties = new BridgeWorkAuthProperties();
        properties.setAllowedOrigins(java.util.List.of("https://bridgework.cloud", "https://www.bridgework.cloud"));
        filter = new RefreshCookieOriginFilter(properties, new ObjectMapper());
    }

    @Test
    void rejectsCrossSiteRefreshRequest() throws Exception {
        MockHttpServletRequest request = request("https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new AssertionError("필터 체인이 호출되면 안 됩니다.");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("UNTRUSTED_ORIGIN");
    }

    @Test
    void allowsConfiguredFrontendOrigin() throws Exception {
        MockHttpServletRequest request = request("https://www.bridgework.cloud");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = {false};

        filter.doFilter(request, response, (servletRequest, servletResponse) -> called[0] = true);

        assertThat(called[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/token/refresh");
        request.addHeader("Origin", origin);
        return request;
    }
}
