package com.bridgework.auth.security;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.exception.InvalidJwtException;
import com.bridgework.auth.service.JwtTokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final BridgeWorkAuthProperties authProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(BridgeWorkAuthProperties authProperties) {
        this.authProperties = authProperties;
        byte[] secretBytes = authProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret은 최소 32바이트 이상이어야 합니다.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public JwtTokenPair issueTokenPair(Long userId, UserRole role) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime accessExpiresAt = now.plus(authProperties.getJwt().getAccessTokenValidity());
        OffsetDateTime refreshExpiresAt = now.plus(authProperties.getJwt().getRefreshTokenValidity());
        String refreshTokenId = UUID.randomUUID().toString();

        String accessToken = buildToken(userId, role, TOKEN_TYPE_ACCESS, accessExpiresAt, UUID.randomUUID().toString(), now);

        String refreshToken = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(refreshExpiresAt.toInstant()))
                .id(refreshTokenId)
                .claim("role", role.name())
                .claim("token_type", TOKEN_TYPE_REFRESH)
                .signWith(signingKey)
                .compact();

        return new JwtTokenPair(accessToken, refreshToken, refreshTokenId, accessExpiresAt, refreshExpiresAt);
    }

    public IssuedAccessToken issueAccessToken(Long userId, UserRole role) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime accessExpiresAt = now.plus(authProperties.getJwt().getAccessTokenValidity());
        String accessToken = buildToken(userId, role, TOKEN_TYPE_ACCESS, accessExpiresAt, UUID.randomUUID().toString(), now);
        return new IssuedAccessToken(accessToken, accessExpiresAt);
    }

    public ParsedJwtToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            String role = claims.get("role", String.class);
            String tokenType = claims.get("token_type", String.class);
            String tokenId = claims.getId();

            if (subject == null || role == null || tokenType == null || tokenId == null) {
                throw new InvalidJwtException("JWT 클레임이 유효하지 않습니다.");
            }

            return new ParsedJwtToken(
                    Long.parseLong(subject),
                    UserRole.valueOf(role),
                    tokenId,
                    tokenType
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidJwtException("페이지가 유효하지 않습니다 다시 로그인 해주세요.");
        }
    }

    private String buildToken(
            Long userId,
            UserRole role,
            String tokenType,
            OffsetDateTime expiresAt,
            String tokenId,
            OffsetDateTime issuedAt
    ) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(Date.from(issuedAt.toInstant()))
                .expiration(Date.from(expiresAt.toInstant()))
                .id(tokenId)
                .claim("role", role.name())
                .claim("token_type", tokenType)
                .signWith(signingKey)
                .compact();
    }

    public record IssuedAccessToken(
            String token,
            OffsetDateTime expiresAt
    ) {
    }
}
