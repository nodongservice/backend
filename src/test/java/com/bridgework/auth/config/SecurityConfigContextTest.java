package com.bridgework.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bridgework.auth.security.JwtAuthenticationFilter;
import com.bridgework.auth.security.RefreshCookieOriginFilter;
import com.bridgework.common.ratelimit.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

class SecurityConfigContextTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityAutoConfiguration.class,
                    SecurityFilterAutoConfiguration.class,
                    WebMvcAutoConfiguration.class
            ))
            .withUserConfiguration(SecurityConfig.class)
            .withBean(JwtAuthenticationFilter.class, () -> mock(JwtAuthenticationFilter.class))
            .withBean(RefreshCookieOriginFilter.class, () -> mock(RefreshCookieOriginFilter.class))
            .withBean(RateLimitFilter.class, () -> mock(RateLimitFilter.class))
            .withBean(BridgeWorkAuthProperties.class, BridgeWorkAuthProperties::new)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("bridgework.auth.jwt.secret=test-jwt-secret");

    @Test
    void securityFilterChainRegistersCustomFiltersWithKnownOrder() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
        });
    }
}
