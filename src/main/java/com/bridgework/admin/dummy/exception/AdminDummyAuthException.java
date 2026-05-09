package com.bridgework.admin.dummy.exception;

import com.bridgework.common.exception.BridgeWorkDomainException;
import org.springframework.http.HttpStatus;

public class AdminDummyAuthException extends BridgeWorkDomainException {

    public AdminDummyAuthException(String errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }
}

