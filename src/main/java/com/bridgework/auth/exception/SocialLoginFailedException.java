package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class SocialLoginFailedException extends AuthDomainException {

    public SocialLoginFailedException(String message) {
        super("SOCIAL_LOGIN_FAILED", HttpStatus.UNAUTHORIZED, message);
    }

    public SocialLoginFailedException(String message, Throwable cause) {
        super("SOCIAL_LOGIN_FAILED", HttpStatus.UNAUTHORIZED, message, cause);
    }
}
