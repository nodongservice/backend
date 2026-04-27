package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends AuthDomainException {

    public DuplicateEmailException() {
        super("DUPLICATE_EMAIL", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
    }
}
