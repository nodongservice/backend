package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends AuthDomainException {

    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다.");
    }
}
