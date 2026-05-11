package com.bridgework.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bridgework.auth.entity.GenderType;
import com.bridgework.common.config.BridgeWorkHealthMonitorProperties;
import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.recommend.config.BridgeWorkRecommendProperties;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FastApiRecommendClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildScoreProfile_includesHomeCoordinatesAndCommuteLimitMinutes() {
        FastApiRecommendClient client = new FastApiRecommendClient(
                null,
                new BridgeWorkRecommendProperties(),
                new BridgeWorkHealthMonitorProperties()
        );

        Map<String, Object> payload = ReflectionTestUtils.invokeMethod(
                client,
                "buildScoreProfile",
                profile()
        );

        assertThat(payload).isNotNull();
        assertThat(payload.get("home_lat")).isEqualTo(37.5665);
        assertThat(payload.get("home_lng")).isEqualTo(126.978);
        assertThat(payload.get("commute_limit_minutes")).isEqualTo(50);
        assertThat(payload.get("mobility_range_km")).isNull();
    }

    private UserProfileResponseDto profile() {
        return new UserProfileResponseDto(
                11L,
                1L,
                true,
                "사무보조",
                "대중교통 50분 이내",
                List.of(),
                List.of(),
                List.of(),
                "지체",
                "사무 경력",
                "대졸",
                "정규직",
                "기본 생성 프로필",
                "홍길동",
                "010-1111-1111",
                "hong@example.com",
                LocalDate.of(1990, 1, 1),
                GenderType.MALE,
                null,
                "서울특별시 중구 세종대로 110",
                null,
                "대졸",
                "졸업",
                "주요 경력",
                null,
                null,
                null,
                "사무보조",
                List.of("엑셀"),
                List.of("컴활"),
                null,
                null,
                null,
                "중증",
                true,
                null,
                null,
                null,
                "즉시",
                List.of("정규직"),
                null,
                null,
                null,
                "자기소개",
                "지원동기",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                37.5665,
                126.978,
                "서울특별시 중구 세종대로 110",
                OffsetDateTime.now()
        );
    }
}
