package com.bridgework.profile.service;

import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import com.bridgework.sync.normalized.NormalizedGeoPoint;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserProfileCommandFacade {

    private final UserProfileService userProfileService;

    public UserProfileCommandFacade(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    public UserProfileResponseDto create(Long userId, UserProfileUpsertRequestDto request) {
        Optional<NormalizedGeoPoint> homeGeoPoint = prepareHomeCoordinates(request.detailAddress());
        return userProfileService.createWithResolvedHomeCoordinates(userId, request, homeGeoPoint);
    }

    public UserProfileResponseDto update(Long userId, Long profileId, UserProfileUpsertRequestDto request) {
        Optional<NormalizedGeoPoint> homeGeoPoint = prepareHomeCoordinates(request.detailAddress());
        return userProfileService.updateWithResolvedHomeCoordinates(userId, profileId, request, homeGeoPoint);
    }

    public Optional<NormalizedGeoPoint> prepareHomeCoordinates(String detailAddress) {
        return userProfileService.prepareHomeCoordinates(detailAddress);
    }
}
