package com.bridgework.profile.exception;

import org.springframework.http.HttpStatus;

public class UserProfileNotFoundException extends ProfileDomainException {

    public UserProfileNotFoundException() {
        super("USER_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "프로필이 존재하지 않습니다.");
    }

    public UserProfileNotFoundException(Long profileId) {
        super("USER_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다. profileId=" + profileId);
    }
}
