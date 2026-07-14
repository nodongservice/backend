package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class SignupCompletionInProgressException extends AuthDomainException {

    public SignupCompletionInProgressException() {
        super("SIGNUP_COMPLETION_IN_PROGRESS", HttpStatus.CONFLICT, "회원가입 완료 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요.");
    }
}
