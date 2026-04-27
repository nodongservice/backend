package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends AuthDomainException {

    public UserNotFoundException() {
        super("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
    }
}
