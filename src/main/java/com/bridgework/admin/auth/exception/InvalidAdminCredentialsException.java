package com.bridgework.admin.auth.exception;

import com.bridgework.auth.exception.AuthDomainException;
import org.springframework.http.HttpStatus;

public class InvalidAdminCredentialsException extends AuthDomainException {

    public InvalidAdminCredentialsException() {
        super("INVALID_ADMIN_CREDENTIALS", HttpStatus.UNAUTHORIZED, "관리자 인증 정보가 올바르지 않습니다.");
    }
}
