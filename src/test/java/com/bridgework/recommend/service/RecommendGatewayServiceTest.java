package com.bridgework.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.auth.entity.GenderType;
import com.bridgework.recommend.dto.RecommendExplainJobDto;
import com.bridgework.recommend.dto.RecommendExplainProfileDto;
import com.bridgework.recommend.dto.RecommendExplainRequestDto;
import com.bridgework.recommend.dto.RecommendExplainResponseDto;
import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.service.UserProfileService;
import com.bridgework.recommend.dto.RecommendJobResponseDto;
import com.bridgework.recommend.dto.RecommendRequestDto;
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
                1L, 1L, "pd_kepad_recruitment", "사업장", "사무보조", "서울", "정규직", "신입",
                "월급", "300만원", "20261231", "20260504", "20260504",
                "무관", "고졸", "무관", "무관", "담당기관", 37.5, 127.0
        );
        when(recommendJobQueryService.getLatestRecruitments()).thenReturn(List.of(job));

        Map<String, Object> response = recommendGatewayService.recommendQuick(
                1L,
                new RecommendRequestDto(false, null)
        );

        assertThat(response).containsKey("results");
        List<?> results = (List<?>) response.get("results");
        assertThat(results).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) results.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> jobPayload = (Map<String, Object>) first.get("job");

        assertThat(jobPayload.get("job_post_id")).isEqualTo(1L);
        assertThat(jobPayload.get("company_name")).isEqualTo("사업장");
        assertThat(first.get("job_fit_score")).isNull();
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

        Map<String, Object> response = recommendGatewayService.recommendMap(
                1L,
                new RecommendRequestDto(true, null)
        );

        assertThat(response).isEqualTo(aiResponse.get("result"));
        List<?> results = (List<?>) response.get("results");
        assertThat(results).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) results.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> jobPayload = (Map<String, Object>) first.get("job");

        assertThat(jobPayload.get("company_name")).isEqualTo("사업장");
        assertThat(jobPayload.get("job_title")).isEqualTo("사무보조");
        assertThat(first.get("total_score")).isEqualTo(88);
    }

    @Test
    void explainRecommendation_whenCalled_thenReturnsParsedExplainResult() {
        RecommendExplainProfileDto profilePayload = new RecommendExplainProfileDto(
                11L, 1L, "홍길동", "서울", null, null, List.of("사무보조"), List.of("엑셀"),
                "고졸", "무관", null, List.of("컴활"), null, List.of("정규직"),
                null, null, null, List.of(), null, null, null, List.of(), List.of(), null
        );
        RecommendExplainRequestDto request = new RecommendExplainRequestDto(
                profilePayload,
                new RecommendExplainJobDto(
                        12345L,
                        "브릿지웍스",
                        "사무보조",
                        "서울",
                        37.5,
                        127.0,
                        "정규직",
                        "신입",
                        "월급",
                        "300만원",
                        "20261231",
                        "무관",
                        "고졸",
                        "무관",
                        "무관",
                        null,
                        "20260504",
                        "pd_kepad_recruitment",
                        99L
                ),
                null,
                null,
                null,
                86,
                List.of("직무 유사도 높음"),
                List.of("출퇴근 시간 혼잡 가능"),
                List.of()
        );

        Map<String, Object> aiResponse = Map.of(
                "code", "SUCCESS",
                "message", "요청이 성공했습니다.",
                "result", Map.of(
                        "short_summary", "직무 적합도와 접근성이 균형 잡힌 공고입니다.",
                        "recommendation_reasons", List.of("직무 유사도 높음"),
                        "caution_points", List.of("출퇴근 시간 혼잡 가능"),
                        "checklist", List.of("면접 전 근무지 동선을 확인하세요."),
                        "used_llm", false
                )
        );

        when(fastApiRecommendClient.requestRecommendationExplain(request)).thenReturn(aiResponse);

        RecommendExplainResponseDto response = recommendGatewayService.explainRecommendation(1L, request);

        assertThat(response.profileId()).isEqualTo(11L);
        assertThat(response.shortSummary()).isEqualTo("직무 적합도와 접근성이 균형 잡힌 공고입니다.");
        assertThat(response.recommendationReasons()).containsExactly("직무 유사도 높음");
        assertThat(response.cautionPoints()).containsExactly("출퇴근 시간 혼잡 가능");
        assertThat(response.checklist()).containsExactly("면접 전 근무지 동선을 확인하세요.");
        assertThat(response.usedLlm()).isFalse();
        assertThat(response.aiResponse()).isEqualTo(aiResponse);
        verify(userProfileService, never()).getProfile(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
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
                "강남구",
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
