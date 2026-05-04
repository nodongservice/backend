package com.bridgework.recommend.exception;

import com.bridgework.common.exception.BridgeWorkDomainException;
import org.springframework.http.HttpStatus;

public class RecommendDomainException extends BridgeWorkDomainException {

    public RecommendDomainException(String errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }

    public RecommendDomainException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(errorCode, httpStatus, message, cause);
    }
}

