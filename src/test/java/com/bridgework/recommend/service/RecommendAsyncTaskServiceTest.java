package com.bridgework.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bridgework.recommend.dto.RecommendRequestDto;
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

    @Test
    void useAiOrDefault_whenAiEnabledMissing_thenTreatsAsAiEnabled() {
        assertThat(RecommendAsyncTaskService.useAiOrDefault(null)).isTrue();
        assertThat(RecommendAsyncTaskService.useAiOrDefault(new RecommendRequestDto(null, 1L))).isTrue();
        assertThat(RecommendAsyncTaskService.useAiOrDefault(new RecommendRequestDto(false, 1L))).isFalse();
    }

    @Test
    void safeLimit_whenAiEnabled_thenDoesNotClampToPageSize() {
        RecommendRequestDto request = new RecommendRequestDto(true, 1L, 154, 0);

        assertThat(RecommendAsyncTaskService.safeLimit(request)).isEqualTo(154);
    }

    @Test
    void safeLimit_whenAiDisabled_thenKeepsPageSizeLimit() {
        RecommendRequestDto request = new RecommendRequestDto(false, null, 154, 0);

        assertThat(RecommendAsyncTaskService.safeLimit(request)).isEqualTo(100);
    }
}
