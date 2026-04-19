package com.bridgework.sync.exception;

import org.springframework.http.HttpStatus;

public class SyncDomainException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public SyncDomainException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public SyncDomainException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
