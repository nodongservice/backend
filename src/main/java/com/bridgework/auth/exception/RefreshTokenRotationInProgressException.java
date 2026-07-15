package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenRotationInProgressException extends AuthDomainException {

    public RefreshTokenRotationInProgressException() {
        super(
                "REFRESH_TOKEN_ROTATION_IN_PROGRESS",
                HttpStatus.CONFLICT,
                "다른 요청에서 로그인 세션을 갱신하고 있습니다. 잠시 후 다시 시도해 주세요."
        );
    }
}
