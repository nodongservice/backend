package com.bridgework.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.auth.entity.GenderType;
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
        Map<String, Object> aiResponse = Map.of(
                "code", "SUCCESS",
                "message", "요청이 성공했습니다.",
                "result", Map.of(
                        "results", List.of(Map.of(
                                "job",
                                Map.ofEntries(
                                        Map.entry("external_id", "ext-1"),
                                        Map.entry("company_name", "사업장"),
                                        Map.entry("job_title", "사무보조"),
                                        Map.entry("work_address", "서울"),
                                        Map.entry("employment_type", "정규직"),
                                        Map.entry("enter_type", "신입"),
                                        Map.entry("salary_type", "월급"),
                                        Map.entry("salary", "300만원"),
                                        Map.entry("term_date", "20261231"),
                                        Map.entry("registered_at", "20260504"),
                                        Map.entry("required_career", "무관"),
                                        Map.entry("required_education", "고졸"),
                                        Map.entry("required_major", "무관"),
                                        Map.entry("required_licenses", "무관"),
                                        Map.entry("work_lat", 37.5),
                                        Map.entry("work_lng", 127.0)
                                ),
                                "total_score", 88
                        ))
                )
        );

        when(userProfileService.getProfiles(1L)).thenReturn(List.of(defaultProfile));
        when(fastApiRecommendClient.requestMapScore(defaultProfile)).thenReturn(aiResponse);

        RecommendResponseDto response = recommendGatewayService.recommendMap(
                1L,
                new RecommendRequestDto(true, null)
        );

        assertThat(response.aiEnabled()).isTrue();
        assertThat(response.profileId()).isEqualTo(11L);
        assertThat(response.aiResponse()).isEqualTo(aiResponse);
        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).externalId()).isEqualTo("ext-1");
        assertThat(response.jobs().get(0).busplaName()).isEqualTo("사업장");
        assertThat(response.jobs().get(0).jobNm()).isEqualTo("사무보조");
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
                GenderType.MALE,
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
