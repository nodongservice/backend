package com.bridgework.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bridgework.auth.entity.UserRole;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenStoreIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RefreshTokenStoreService storeService;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        storeService = new RefreshTokenStoreService(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void rotateConsumesPreviousTokenAtomicallyAndSeparatesRoles() {
        Duration ttl = Duration.ofMinutes(5);
        storeService.save(1L, UserRole.USER, "old-id", "user-session", "old-token", ttl);
        storeService.save(1L, UserRole.ADMIN, "admin-id", "admin-session", "admin-token", ttl);

        assertThat(storeService.rotate(
                1L, UserRole.USER, "old-id", "old-token", "new-id", "new-token", "user-session", ttl
        )).isEqualTo(RefreshTokenStoreService.RotationResult.ROTATED);
        assertThat(storeService.rotate(
                1L, UserRole.USER, "old-id", "old-token", "other-id", "other-token", "user-session", ttl
        )).isEqualTo(RefreshTokenStoreService.RotationResult.IN_PROGRESS);
        assertThat(storeService.matches(1L, UserRole.USER, "new-id", "new-token")).isTrue();
        assertThat(storeService.matches(1L, UserRole.ADMIN, "admin-id", "admin-token")).isTrue();
        assertThat(storeService.isSessionActive(1L, UserRole.USER, "user-session")).isTrue();

        redisTemplate.delete("auth:refresh:USER:1:old-id");
        assertThat(storeService.rotate(
                1L, UserRole.USER, "old-id", "old-token", "replay-id", "replay-token", "user-session", ttl
        )).isEqualTo(RefreshTokenStoreService.RotationResult.REUSE_DETECTED);
        assertThat(storeService.isSessionActive(1L, UserRole.USER, "user-session")).isFalse();

        storeService.delete(1L, UserRole.USER, "new-id", "user-session");
        assertThat(storeService.isSessionActive(1L, UserRole.USER, "user-session")).isFalse();
    }
}
