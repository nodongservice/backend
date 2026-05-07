package com.bridgework.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bridgework.auth")
public class BridgeWorkAuthProperties {

    @Valid
    @NotNull
    private Jwt jwt = new Jwt();

    @Valid
    @NotNull
    private OAuth2 oauth2 = new OAuth2();

    @NotNull
    private Duration signupSessionValidity = Duration.ofMinutes(20);

    @NotNull
    private List<String> allowedOrigins = new ArrayList<>();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public OAuth2 getOauth2() {
        return oauth2;
    }

    public void setOauth2(OAuth2 oauth2) {
        this.oauth2 = oauth2;
    }

    public Duration getSignupSessionValidity() {
        return signupSessionValidity;
    }

    public void setSignupSessionValidity(Duration signupSessionValidity) {
        this.signupSessionValidity = signupSessionValidity;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public static class Jwt {

        @NotBlank
        private String secret;

        @NotBlank
        private String issuer = "bridgework";

        @NotNull
        private Duration accessTokenValidity = Duration.ofMinutes(15);

        @NotNull
        private Duration refreshTokenValidity = Duration.ofDays(14);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getAccessTokenValidity() {
            return accessTokenValidity;
        }

        public void setAccessTokenValidity(Duration accessTokenValidity) {
            this.accessTokenValidity = accessTokenValidity;
        }

        public Duration getRefreshTokenValidity() {
            return refreshTokenValidity;
        }

        public void setRefreshTokenValidity(Duration refreshTokenValidity) {
            this.refreshTokenValidity = refreshTokenValidity;
        }
    }

    public static class OAuth2 {

        @Valid
        @NotNull
        private Provider kakao = new Provider();

        @Valid
        @NotNull
        private Provider naver = new Provider();

        public Provider getKakao() {
            return kakao;
        }

        public void setKakao(Provider kakao) {
            this.kakao = kakao;
        }

        public Provider getNaver() {
            return naver;
        }

        public void setNaver(Provider naver) {
            this.naver = naver;
        }
    }

    public static class Provider {

        private String clientId;

        private String clientSecret;

        private String tokenUri;

        private String userInfoUri;

        private String redirectUri;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getUserInfoUri() {
            return userInfoUri;
        }

        public void setUserInfoUri(String userInfoUri) {
            this.userInfoUri = userInfoUri;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }

}
