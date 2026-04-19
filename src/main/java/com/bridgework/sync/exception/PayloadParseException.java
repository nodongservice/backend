package com.bridgework.sync.exception;

import org.springframework.http.HttpStatus;

public class PayloadParseException extends SyncDomainException {

    public PayloadParseException(String message, Throwable cause) {
        super("PAYLOAD_PARSE_ERROR", HttpStatus.BAD_GATEWAY, message, cause);
    }
}
