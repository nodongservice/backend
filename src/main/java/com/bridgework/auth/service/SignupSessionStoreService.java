package com.bridgework.auth.service;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.exception.SignupSessionNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SignupSessionStoreService {

    private static final String SIGNUP_SESSION_PREFIX = "auth:signup:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BridgeWorkAuthProperties authProperties;

    public SignupSessionStoreService(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     BridgeWorkAuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authProperties = authProperties;
    }

    public String createSession(SocialSignupSessionData signupSessionData) {
        String token = generateSecureToken();
        String key = SIGNUP_SESSION_PREFIX + token;

        try {
            String value = objectMapper.writeValueAsString(signupSessionData);
            redisTemplate.opsForValue().set(key, value, authProperties.getSignupSessionValidity());
            return token;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("회원가입 세션 저장에 실패했습니다.", exception);
        }
    }

    public SocialSignupSessionData getRequiredSession(String signupToken) {
        return getSession(signupToken).orElseThrow(SignupSessionNotFoundException::new);
    }

    public Optional<SocialSignupSessionData> getSession(String signupToken) {
        String key = SIGNUP_SESSION_PREFIX + signupToken;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(value, SocialSignupSessionData.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("회원가입 세션 파싱에 실패했습니다.", exception);
        }
    }

    public void deleteSession(String signupToken) {
        redisTemplate.delete(SIGNUP_SESSION_PREFIX + signupToken);
    }

    private String generateSecureToken() {
        // 가입 완료 토큰은 예측 불가능한 난수로 발급해 탈취 재현 가능성을 낮춘다.
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
