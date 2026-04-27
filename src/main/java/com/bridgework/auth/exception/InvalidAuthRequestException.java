package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidAuthRequestException extends AuthDomainException {

    public InvalidAuthRequestException(String message) {
        super("INVALID_AUTH_REQUEST", HttpStatus.BAD_REQUEST, message);
    }
}
