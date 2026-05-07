package com.bridgework.sync.exception;

import org.springframework.http.HttpStatus;

public class ExternalApiException extends SyncDomainException {

    private final boolean retryable;

    public ExternalApiException(String message) {
        this(message, false);
    }

    public ExternalApiException(String message, boolean retryable) {
        super("EXTERNAL_API_ERROR", HttpStatus.BAD_GATEWAY, message);
        this.retryable = retryable;
    }

    public ExternalApiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public ExternalApiException(String message, Throwable cause, boolean retryable) {
        super("EXTERNAL_API_ERROR", HttpStatus.BAD_GATEWAY, message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static ExternalApiException retryable(String message) {
        return new ExternalApiException(message, true);
    }

    public static ExternalApiException retryable(String message, Throwable cause) {
        return new ExternalApiException(message, cause, true);
    }
}
