package com.bridgework.auth.service;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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

    public void deleteAllByUserId(Long userId) {
        String pattern = REFRESH_TOKEN_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.execute((RedisConnection connection) -> {
            Set<String> matchedKeys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    matchedKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
                return Set.of();
            }
            return matchedKeys;
        });

        if (keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
    }

    private String buildKey(Long userId, String tokenId) {
        return REFRESH_TOKEN_PREFIX + userId + ":" + tokenId;
    }
}
