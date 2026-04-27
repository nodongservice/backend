package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class SignupSessionNotFoundException extends AuthDomainException {

    public SignupSessionNotFoundException() {
        super("SIGNUP_SESSION_NOT_FOUND", HttpStatus.UNAUTHORIZED, "회원가입 세션이 만료되었거나 유효하지 않습니다.");
    }
}
