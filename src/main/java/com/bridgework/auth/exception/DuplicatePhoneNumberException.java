package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class DuplicatePhoneNumberException extends AuthDomainException {

    public DuplicatePhoneNumberException() {
        super("DUPLICATE_PHONE_NUMBER", HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다.");
    }
}
