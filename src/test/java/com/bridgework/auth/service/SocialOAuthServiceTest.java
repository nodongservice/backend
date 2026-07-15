package com.bridgework.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.exception.SocialLoginFailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class SocialOAuthServiceTest {

    private SocialOAuthService socialOAuthService;

    @BeforeEach
    void setUp() {
        socialOAuthService = new SocialOAuthService(
                WebClient.create(),
                new ObjectMapper(),
                new BridgeWorkAuthProperties()
        );
    }

    @Test
    void resolveRedirectUriUsesConfiguredValueWhenRequestMatches() {
        String configured = "https://www.bridgework.cloud/auth/kakao/callback";

        assertThat(socialOAuthService.resolveRedirectUri(configured, configured)).isEqualTo(configured);
        assertThat(socialOAuthService.resolveRedirectUri(null, configured)).isEqualTo(configured);
    }

    @Test
    void resolveRedirectUriRejectsClientSuppliedDifferentOrigin() {
        assertThatThrownBy(() -> socialOAuthService.resolveRedirectUri(
                "https://attacker.example/auth/kakao/callback",
                "https://www.bridgework.cloud/auth/kakao/callback"
        )).isInstanceOf(SocialLoginFailedException.class)
                .hasMessageContaining("허용되지 않은 OAuth 리다이렉트 URI");
    }
}
