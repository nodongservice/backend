package com.bridgework.sync.exception;

import org.springframework.http.HttpStatus;

public class ExternalApiException extends SyncDomainException {

    public ExternalApiException(String message) {
        super("EXTERNAL_API_ERROR", HttpStatus.BAD_GATEWAY, message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super("EXTERNAL_API_ERROR", HttpStatus.BAD_GATEWAY, message, cause);
    }
}
