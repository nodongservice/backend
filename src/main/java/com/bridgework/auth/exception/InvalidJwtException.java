package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidJwtException extends AuthDomainException {

    public InvalidJwtException(String message) {
        super("INVALID_JWT", HttpStatus.UNAUTHORIZED, message);
    }
}
