package com.bridgework.common.exception;

import org.springframework.http.HttpStatus;

public class BridgeWorkDomainException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public BridgeWorkDomainException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public BridgeWorkDomainException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
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
