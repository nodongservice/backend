package com.bridgework.recommend.service;

import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.service.UserProfileService;
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
}
