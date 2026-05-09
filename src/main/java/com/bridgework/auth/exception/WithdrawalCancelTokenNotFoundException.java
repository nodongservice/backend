package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class WithdrawalCancelTokenNotFoundException extends AuthDomainException {

    public WithdrawalCancelTokenNotFoundException() {
        super("WITHDRAWAL_CANCEL_TOKEN_NOT_FOUND", HttpStatus.NOT_FOUND, "탈퇴 취소 토큰이 유효하지 않거나 만료되었습니다.");
    }
}
