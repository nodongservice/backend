package com.bridgework.auth.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenStoreService {

    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenStoreService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long userId, String tokenId, String refreshToken, Duration ttl) {
        String key = buildKey(userId, tokenId);
        String tokenHash = TokenHashUtils.sha256(refreshToken);
        redisTemplate.opsForValue().set(key, tokenHash, ttl);
    }

    public boolean matches(Long userId, String tokenId, String refreshToken) {
        String key = buildKey(userId, tokenId);
        String storedHash = redisTemplate.opsForValue().get(key);
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return storedHash.equals(TokenHashUtils.sha256(refreshToken));
    }

    public void delete(Long userId, String tokenId) {
        redisTemplate.delete(buildKey(userId, tokenId));
    }

    private String buildKey(Long userId, String tokenId) {
        return REFRESH_TOKEN_PREFIX + userId + ":" + tokenId;
    }
}
