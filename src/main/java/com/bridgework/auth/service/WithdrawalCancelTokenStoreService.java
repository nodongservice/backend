package com.bridgework.auth.service;

import com.bridgework.auth.exception.WithdrawalCancelTokenNotFoundException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class WithdrawalCancelTokenStoreService {

    private static final String WITHDRAWAL_CANCEL_TOKEN_PREFIX = "auth:withdraw-cancel:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    public WithdrawalCancelTokenStoreService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String createToken(Long userId, Duration ttl) {
        String token = generateSecureToken();
        redisTemplate.opsForValue().set(WITHDRAWAL_CANCEL_TOKEN_PREFIX + token, String.valueOf(userId), ttl);
        return token;
    }

    public Long getRequiredUserId(String token) {
        String value = redisTemplate.opsForValue().get(WITHDRAWAL_CANCEL_TOKEN_PREFIX + token);
        if (value == null || value.isBlank()) {
            throw new WithdrawalCancelTokenNotFoundException();
        }
        return Long.parseLong(value);
    }

    public void deleteToken(String token) {
        redisTemplate.delete(WITHDRAWAL_CANCEL_TOKEN_PREFIX + token);
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
