package com.bridgework.onboarding.exception;

import com.bridgework.common.exception.BridgeWorkDomainException;
import org.springframework.http.HttpStatus;

public class OnboardingDomainException extends BridgeWorkDomainException {

    public OnboardingDomainException(String errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }
}
