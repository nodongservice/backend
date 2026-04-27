package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AuthDomainException {

    public UnauthorizedException() {
        super("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
    }
}
