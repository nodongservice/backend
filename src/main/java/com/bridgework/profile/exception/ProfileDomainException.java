package com.bridgework.profile.exception;

import com.bridgework.common.exception.BridgeWorkDomainException;
import org.springframework.http.HttpStatus;

public class ProfileDomainException extends BridgeWorkDomainException {

    public ProfileDomainException(String errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }
}
