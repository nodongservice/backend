package com.bridgework.profile.exception;

import org.springframework.http.HttpStatus;

public class ProfileOcrDomainException extends ProfileDomainException {

    public ProfileOcrDomainException(String errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }
}
