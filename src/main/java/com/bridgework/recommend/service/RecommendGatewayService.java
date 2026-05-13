package com.bridgework.recommend.service;

import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.service.UserProfileService;
import com.bridgework.recommend.dto.RecommendExplainRequestDto;
import com.bridgework.recommend.dto.RecommendExplainResponseDto;
import com.bridgework.recommend.dto.RecommendJobResponseDto;
import com.bridgework.recommend.dto.RecommendRequestDto;
import com.bridgework.recommend.exception.RecommendDomainException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RecommendGatewayService {

    private final UserProfileService userProfileService;
    private final RecommendJobQueryService recommendJobQueryService;
    private final FastApiRecommendClient fastApiRecommendClient;

    public RecommendGatewayService(UserProfileService userProfileService,
                                   RecommendJobQueryService recommendJobQueryService,
                                   FastApiRecommendClient fastApiRecommendClient) {
        this.userProfileService = userProfileService;
        this.recommendJobQueryService = recommendJobQueryService;
        this.fastApiRecommendClient = fastApiRecommendClient;
    }

    public Map<String, Object> recommendQuick(Long userId, RecommendRequestDto request) {
        if (request == null || !request.useAi()) {
            return buildQuickFallbackResult(recommendJobQueryService.getLatestRecruitments());
        }

        UserProfileResponseDto profile = resolveSelectedProfile(userId, request.profileId());
        Map<String, Object> aiResponse = fastApiRecommendClient.requestQuickScore(profile);
        return extractResult(aiResponse);
    }

    public Map<String, Object> recommendMap(Long userId, RecommendRequestDto request) {
        if (request == null || !request.useAi()) {
            return buildMapFallbackResult(recommendJobQueryService.getLatestRecruitments());
        }

        UserProfileResponseDto profile = resolveSelectedProfile(userId, request.profileId());
        Map<String, Object> aiResponse = fastApiRecommendClient.requestMapScore(profile);
        return extractResult(aiResponse);
    }

    public RecommendExplainResponseDto explainRecommendation(Long userId, RecommendExplainRequestDto request) {
        Map<String, Object> aiResponse = fastApiRecommendClient.requestRecommendationExplain(request);
        Map<String, Object> result = extractResult(aiResponse);

        return new RecommendExplainResponseDto(
                request.profile() == null ? null : request.profile().profileId(),
                asString(result.get("short_summary")),
                asStringList(result.get("recommendation_reasons")),
                asStringList(result.get("caution_points")),
                asStringList(result.get("checklist")),
                asBoolean(result.get("used_llm")),
                aiResponse
        );
    }

    private UserProfileResponseDto resolveSelectedProfile(Long userId, Long profileId) {
        if (profileId != null) {
            return userProfileService.getProfile(userId, profileId);
        }

        List<UserProfileResponseDto> profiles = userProfileService.getProfiles(userId);
        if (profiles.isEmpty()) {
            throw new RecommendDomainException(
                    "PROFILE_REQUIRED",
                    HttpStatus.BAD_REQUEST,
                    "AI 추천을 사용하려면 프로필이 필요합니다."
            );
        }

        return profiles.get(0);
    }

    private Map<String, Object> buildQuickFallbackResult(List<RecommendJobResponseDto> jobs) {
        List<Map<String, Object>> results = jobs.stream()
                .map(job -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("job", toFastApiJob(job));
                    item.put("job_fit_score", null);
                    item.put("reasons", List.of());
                    item.put("risk_factors", List.of());
                    item.put("evidence_items", List.of());
                    return item;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("results", results);
        return result;
    }

    private Map<String, Object> buildMapFallbackResult(List<RecommendJobResponseDto> jobs) {
        List<Map<String, Object>> results = jobs.stream()
                .map(job -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("job", toFastApiJob(job));
                    item.put("score_detail", buildFallbackMapScoreDetail(job));
                    item.put("total_score", fallbackAccessibilityScore(job));
                    item.put("reasons", List.of());
                    item.put("risk_factors", buildFallbackMapRisks(job));
                    item.put("evidence_items", buildFallbackEvidenceItems(job));
                    return item;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("results", results);
        return result;
    }

    private Map<String, Object> buildFallbackMapScoreDetail(RecommendJobResponseDto job) {
        Map<String, Object> scoreDetail = new LinkedHashMap<>();
        scoreDetail.put("job_fit_score", null);
        scoreDetail.put("work_condition_score", null);
        scoreDetail.put("disability_support_score", null);
        scoreDetail.put("work_environment_score", null);
        scoreDetail.put("company_stability_score", null);
        scoreDetail.put("accessibility_score", fallbackAccessibilityScore(job));
        return scoreDetail;
    }

    private Integer fallbackAccessibilityScore(RecommendJobResponseDto job) {
        return job.geoLatitude() == null || job.geoLongitude() == null ? null : 50;
    }

    private List<String> buildFallbackMapRisks(RecommendJobResponseDto job) {
        if (job.geoLatitude() == null || job.geoLongitude() == null) {
            return List.of("근무지 좌표가 없어 접근성 평가는 추가 확인이 필요합니다.");
        }
        return List.of("AI 접근성 근거 조회가 비활성화되어 상세 근거는 추가 확인이 필요합니다.");
    }

    private List<Map<String, Object>> buildFallbackEvidenceItems(RecommendJobResponseDto job) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("source_type", "KEPAD_RECRUITMENT");
        evidence.put("source_name", "한국장애인고용공단 장애인 구인 실시간 현황");
        evidence.put("description", "공고 원천 데이터와 근무지 좌표를 기준으로 표시했습니다.");
        evidence.put("distance_meters", null);
        evidence.put("source_table", job.sourceTable());
        evidence.put("record_id", job.sourceId());

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("company_name", job.busplaName());
        fields.put("job_title", job.jobNm());
        fields.put("work_address", job.compAddr());
        fields.put("work_lat", job.geoLatitude());
        fields.put("work_lng", job.geoLongitude());
        evidence.put("fields", fields);
        return List.of(evidence);
    }

    private Map<String, Object> toFastApiJob(RecommendJobResponseDto job) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job_post_id", job.jobPostId());
        payload.put("company_name", job.busplaName());
        payload.put("job_title", job.jobNm());
        payload.put("work_address", job.compAddr());
        payload.put("work_lat", job.geoLatitude());
        payload.put("work_lng", job.geoLongitude());
        payload.put("employment_type", job.empType());
        payload.put("enter_type", job.enterType());
        payload.put("salary_type", job.salaryType());
        payload.put("salary", job.salary());
        payload.put("term_date", job.termDate());
        payload.put("required_career", job.reqCareer());
        payload.put("required_education", job.reqEduc());
        payload.put("required_major", job.reqMajor());
        payload.put("required_licenses", job.reqLicens());
        payload.put("environment", Map.of());
        payload.put("agency_name", job.regagnName());
        payload.put("registered_at", job.regDt());
        payload.put("source_table", job.sourceTable());
        payload.put("source_id", job.sourceId());
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractResult(Map<String, Object> aiResponse) {
        Object result = aiResponse.get("result");
        if (result instanceof Map<?, ?> resultMap) {
            return (Map<String, Object>) resultMap;
        }
        throw new RecommendDomainException(
                "FASTAPI_INVALID_RESPONSE",
                HttpStatus.BAD_GATEWAY,
                "FastAPI 설명 응답 형식이 예상과 다릅니다. (result 필요)"
        );
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(String::valueOf)
                .toList();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
