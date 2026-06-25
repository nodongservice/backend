package com.bridgework.notice.exception;

import com.bridgework.common.exception.BridgeWorkDomainException;
import org.springframework.http.HttpStatus;

public class NoticeDomainException extends BridgeWorkDomainException {

    public NoticeDomainException(String errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }
}
