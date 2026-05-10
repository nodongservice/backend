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
                    item.put("score_detail", null);
                    item.put("total_score", null);
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
