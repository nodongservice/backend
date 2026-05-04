package com.bridgework.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.service.UserProfileService;
import com.bridgework.recommend.dto.RecommendJobResponseDto;
import com.bridgework.recommend.dto.RecommendRequestDto;
import com.bridgework.recommend.dto.RecommendResponseDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendGatewayServiceTest {

    @Mock
    private UserProfileService userProfileService;
    @Mock
    private RecommendJobQueryService recommendJobQueryService;
    @Mock
    private FastApiRecommendClient fastApiRecommendClient;

    private RecommendGatewayService recommendGatewayService;

    @BeforeEach
    void setUp() {
        recommendGatewayService = new RecommendGatewayService(
                userProfileService,
                recommendJobQueryService,
                fastApiRecommendClient
        );
    }

    @Test
    void recommendQuick_whenAiDisabled_thenReturnsDbRecruitments() {
        RecommendJobResponseDto job = new RecommendJobResponseDto(
                "ext-1", "사업장", "사무보조", "서울", "정규직", "신입",
                "월급", "300만원", "20261231", "20260504", "20260504",
                "무관", "고졸", "무관", "무관", 37.5, 127.0
        );
        when(recommendJobQueryService.getLatestRecruitments()).thenReturn(List.of(job));

        RecommendResponseDto response = recommendGatewayService.recommendQuick(
                1L,
                new RecommendRequestDto(false, null)
        );

        assertThat(response.aiEnabled()).isFalse();
        assertThat(response.jobs()).hasSize(1);
        assertThat(response.aiResponse()).isNull();
        verify(fastApiRecommendClient, never()).requestQuickScore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recommendMap_whenAiEnabledAndProfileIdMissing_thenUsesDefaultProfileAndCallsFastApi() {
        UserProfileResponseDto defaultProfile = profile(11L, true);
        Map<String, Object> aiResponse = Map.of("jobs", List.of(Map.of("externalId", "ext-1", "totalScore", 88.5)));

        when(userProfileService.getProfiles(1L)).thenReturn(List.of(defaultProfile));
        when(fastApiRecommendClient.requestMapScore(defaultProfile)).thenReturn(aiResponse);

        RecommendResponseDto response = recommendGatewayService.recommendMap(
                1L,
                new RecommendRequestDto(true, null)
        );

        assertThat(response.aiEnabled()).isTrue();
        assertThat(response.profileId()).isEqualTo(11L);
        assertThat(response.aiResponse()).isEqualTo(aiResponse);
        assertThat(response.jobs()).isEmpty();
    }

    private UserProfileResponseDto profile(Long profileId, boolean isDefault) {
        return new UserProfileResponseDto(
                profileId,
                1L,
                isDefault,
                "사무보조",
                "30분",
                List.of(),
                List.of(),
                List.of(),
                "지체",
                "사무 경력",
                "대졸",
                "정규직",
                "홍길동",
                "010-1111-1111",
                "hong@example.com",
                LocalDate.of(1990, 1, 1),
                null,
                "서울",
                "강남구",
                null,
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
                true,
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
                OffsetDateTime.now()
        );
    }
}

