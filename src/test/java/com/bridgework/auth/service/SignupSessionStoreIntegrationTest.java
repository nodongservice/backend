package com.bridgework.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
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
class SignupSessionStoreIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static SignupSessionStoreService storeService;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        storeService = new SignupSessionStoreService(
                new StringRedisTemplate(connectionFactory),
                new ObjectMapper(),
                new BridgeWorkAuthProperties()
        );
    }

    @AfterAll
    static void tearDownRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void completionLockAllowsOnlyOneOwnerAndUsesCompareAndDelete() {
        Optional<String> firstOwner = storeService.tryAcquireCompletionLock("signup-token");

        assertThat(firstOwner).isPresent();
        assertThat(storeService.tryAcquireCompletionLock("signup-token")).isEmpty();

        storeService.releaseCompletionLock("signup-token", "different-owner");
        assertThat(storeService.tryAcquireCompletionLock("signup-token")).isEmpty();

        storeService.releaseCompletionLock("signup-token", firstOwner.orElseThrow());
        assertThat(storeService.tryAcquireCompletionLock("signup-token")).isPresent();
    }
}
