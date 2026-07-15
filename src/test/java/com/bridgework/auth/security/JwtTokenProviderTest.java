package com.bridgework.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.exception.InvalidJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "bridgework-test-jwt-secret-at-least-32-bytes";
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        BridgeWorkAuthProperties properties = new BridgeWorkAuthProperties();
        properties.getJwt().setSecret(SECRET);
        properties.getJwt().setIssuer("bridgework");
        properties.getJwt().setAccessTokenValidity(Duration.ofMinutes(15));
        properties.getJwt().setRefreshTokenValidity(Duration.ofDays(14));
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void issuedRefreshTokenContainsExpectedIssuerAndType() {
        String refreshToken = jwtTokenProvider.issueTokenPair(5L, UserRole.USER).refreshToken();

        ParsedJwtToken parsed = jwtTokenProvider.parse(refreshToken);

        assertThat(parsed.userId()).isEqualTo(5L);
        assertThat(parsed.role()).isEqualTo(UserRole.USER);
        assertThat(parsed.tokenType()).isEqualTo(JwtTokenProvider.TOKEN_TYPE_REFRESH);
    }

    @Test
    void parseRejectsTokenFromDifferentIssuerEvenWhenSignatureMatches() {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject("5")
                .issuer("different-service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .id("token-id")
                .claim("role", UserRole.USER.name())
                .claim("token_type", JwtTokenProvider.TOKEN_TYPE_ACCESS)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtTokenProvider.parse(token))
                .isInstanceOf(InvalidJwtException.class);
    }
}
