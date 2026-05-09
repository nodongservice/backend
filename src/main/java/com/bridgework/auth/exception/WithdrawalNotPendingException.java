package com.bridgework.auth.exception;

import org.springframework.http.HttpStatus;

public class WithdrawalNotPendingException extends AuthDomainException {

    public WithdrawalNotPendingException() {
        super("WITHDRAWAL_NOT_PENDING", HttpStatus.BAD_REQUEST, "탈퇴 신청 상태의 계정이 아닙니다.");
    }
}
