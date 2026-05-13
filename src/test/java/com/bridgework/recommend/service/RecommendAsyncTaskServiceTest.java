package com.bridgework.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RecommendAsyncTaskServiceTest {

    @Test
    void ttlUntilNextCacheBoundary_expiresAtTodayTwoAmBeforeBoundary() {
        Duration ttl = RecommendAsyncTaskService.ttlUntilNextCacheBoundary(
                LocalDateTime.of(2026, 5, 14, 1, 30)
        );

        assertThat(ttl).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void ttlUntilNextCacheBoundary_expiresAtTomorrowTwoAmAtBoundary() {
        Duration ttl = RecommendAsyncTaskService.ttlUntilNextCacheBoundary(
                LocalDateTime.of(2026, 5, 14, 2, 0)
        );

        assertThat(ttl).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void ttlUntilNextCacheBoundary_expiresAtTomorrowTwoAmAfterBoundary() {
        Duration ttl = RecommendAsyncTaskService.ttlUntilNextCacheBoundary(
                LocalDateTime.of(2026, 5, 14, 3, 15)
        );

        assertThat(ttl).isEqualTo(Duration.ofHours(22).plusMinutes(45));
    }
}
