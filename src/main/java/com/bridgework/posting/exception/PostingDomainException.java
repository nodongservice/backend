package com.bridgework.posting.exception;

import com.bridgework.common.exception.BridgeWorkDomainException;
import org.springframework.http.HttpStatus;

public class PostingDomainException extends BridgeWorkDomainException {

    public PostingDomainException(String errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }

    public PostingDomainException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(errorCode, httpStatus, message, cause);
    }
}
