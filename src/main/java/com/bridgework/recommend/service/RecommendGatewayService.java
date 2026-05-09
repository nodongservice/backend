package com.bridgework.recommend.service;

import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.service.UserProfileService;
import com.bridgework.recommend.dto.RecommendExplainRequestDto;
import com.bridgework.recommend.dto.RecommendExplainResponseDto;
import com.bridgework.recommend.dto.RecommendRequestDto;
import com.bridgework.recommend.dto.RecommendResponseDto;
import com.bridgework.recommend.exception.RecommendDomainException;
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

    public RecommendResponseDto recommendQuick(Long userId, RecommendRequestDto request) {
        if (request == null || !request.useAi()) {
            return RecommendResponseDto.fromDb(recommendJobQueryService.getLatestRecruitments());
        }

        UserProfileResponseDto profile = resolveSelectedProfile(userId, request.profileId());
        Map<String, Object> aiResponse = fastApiRecommendClient.requestQuickScore(profile);
        return RecommendResponseDto.fromAi(profile.profileId(), aiResponse);
    }

    public RecommendResponseDto recommendMap(Long userId, RecommendRequestDto request) {
        if (request == null || !request.useAi()) {
            return RecommendResponseDto.fromDb(recommendJobQueryService.getLatestRecruitments());
        }

        UserProfileResponseDto profile = resolveSelectedProfile(userId, request.profileId());
        Map<String, Object> aiResponse = fastApiRecommendClient.requestMapScore(profile);
        return RecommendResponseDto.fromAi(profile.profileId(), aiResponse);
    }

    public RecommendExplainResponseDto explainRecommendation(Long userId, RecommendExplainRequestDto request) {
        UserProfileResponseDto profile = resolveSelectedProfile(userId, request.profileId());
        Map<String, Object> aiResponse = fastApiRecommendClient.requestRecommendationExplain(profile, request);
        Map<String, Object> result = extractResult(aiResponse);

        return new RecommendExplainResponseDto(
                profile.profileId(),
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
