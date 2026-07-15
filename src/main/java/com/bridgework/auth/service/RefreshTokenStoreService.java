package com.bridgework.auth.service;

import com.bridgework.auth.entity.UserRole;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenStoreService {

    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    private static final String SESSION_PREFIX = "auth:session:";
    private static final String ROTATED_MARKER = "rotated";
    private static final long ROTATION_GRACE_MILLIS = 10_000L;
    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
            redis.call('PSETEX', KEYS[2], ARGV[2], 'active')
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if stored == ARGV[4] then
                return 2
            end
            if not stored or stored ~= ARGV[1] then
                if redis.call('EXISTS', KEYS[3]) then
                    redis.call('DEL', KEYS[3])
                    return -1
                end
                return 0
            end
            if not redis.call('EXISTS', KEYS[3]) then
                return 0
            end
            redis.call('PSETEX', KEYS[1], ARGV[5], ARGV[4])
            redis.call('PSETEX', KEYS[2], ARGV[3], ARGV[2])
            redis.call('PEXPIRE', KEYS[3], ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenStoreService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long userId,
                     UserRole role,
                     String tokenId,
                     String sessionId,
                     String refreshToken,
                     Duration ttl) {
        String tokenHash = TokenHashUtils.sha256(refreshToken);
        redisTemplate.execute(
                SAVE_SCRIPT,
                java.util.List.of(
                        buildKey(userId, role, tokenId),
                        buildSessionKey(userId, role, sessionId)
                ),
                tokenHash,
                String.valueOf(ttl.toMillis())
        );
    }

    public RotationResult rotate(Long userId,
                                 UserRole role,
                                 String currentTokenId,
                                 String currentRefreshToken,
                                 String nextTokenId,
                                 String nextRefreshToken,
                                 String sessionId,
                                 Duration ttl) {
        Long rotated = redisTemplate.execute(
                ROTATE_SCRIPT,
                java.util.List.of(
                        buildKey(userId, role, currentTokenId),
                        buildKey(userId, role, nextTokenId),
                        buildSessionKey(userId, role, sessionId)
                ),
                TokenHashUtils.sha256(currentRefreshToken),
                TokenHashUtils.sha256(nextRefreshToken),
                String.valueOf(ttl.toMillis()),
                ROTATED_MARKER,
                String.valueOf(ROTATION_GRACE_MILLIS)
        );
        if (Long.valueOf(1L).equals(rotated)) {
            return RotationResult.ROTATED;
        }
        if (Long.valueOf(2L).equals(rotated)) {
            return RotationResult.IN_PROGRESS;
        }
        if (Long.valueOf(-1L).equals(rotated)) {
            return RotationResult.REUSE_DETECTED;
        }
        return RotationResult.INVALID;
    }

    public boolean matches(Long userId, UserRole role, String tokenId, String refreshToken) {
        String key = buildKey(userId, role, tokenId);
        String storedHash = redisTemplate.opsForValue().get(key);
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return storedHash.equals(TokenHashUtils.sha256(refreshToken));
    }

    public void delete(Long userId, UserRole role, String tokenId, String sessionId) {
        redisTemplate.delete(java.util.List.of(
                buildKey(userId, role, tokenId),
                buildSessionKey(userId, role, sessionId)
        ));
    }

    public boolean isSessionActive(Long userId, UserRole role, String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildSessionKey(userId, role, sessionId)));
    }

    public void deleteAllByUserId(Long userId, UserRole role) {
        Set<String> refreshKeys = scanKeys(REFRESH_TOKEN_PREFIX + role.name() + ":" + userId + ":*");
        Set<String> sessionKeys = scanKeys(SESSION_PREFIX + role.name() + ":" + userId + ":*");
        Set<String> keys = new HashSet<>(refreshKeys);
        keys.addAll(sessionKeys);

        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = redisTemplate.execute((RedisConnection connection) -> {
            Set<String> matchedKeys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    matchedKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("인증 세션 키 조회에 실패했습니다.", exception);
            }
            return matchedKeys;
        });
        return keys == null ? Set.of() : keys;
    }

    private String buildKey(Long userId, UserRole role, String tokenId) {
        return REFRESH_TOKEN_PREFIX + role.name() + ":" + userId + ":" + tokenId;
    }

    private String buildSessionKey(Long userId, UserRole role, String sessionId) {
        return SESSION_PREFIX + role.name() + ":" + userId + ":" + sessionId;
    }

    public enum RotationResult {
        ROTATED,
        IN_PROGRESS,
        REUSE_DETECTED,
        INVALID
    }
}
