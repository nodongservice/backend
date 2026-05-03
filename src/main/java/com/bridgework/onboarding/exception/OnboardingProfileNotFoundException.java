package com.bridgework.onboarding.exception;

import org.springframework.http.HttpStatus;

public class OnboardingProfileNotFoundException extends OnboardingDomainException {

    public OnboardingProfileNotFoundException() {
        super("ONBOARDING_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "온보딩 프로필이 존재하지 않습니다.");
    }

    public OnboardingProfileNotFoundException(Long profileId) {
        super("ONBOARDING_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다. profileId=" + profileId);
    }
}
