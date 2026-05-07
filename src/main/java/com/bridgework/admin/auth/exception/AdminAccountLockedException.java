package com.bridgework.admin.auth.exception;

import com.bridgework.auth.exception.AuthDomainException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;

public class AdminAccountLockedException extends AuthDomainException {

    public AdminAccountLockedException(OffsetDateTime lockedUntil) {
        super(
                "ADMIN_ACCOUNT_LOCKED",
                HttpStatus.LOCKED,
                buildMessage(lockedUntil)
        );
    }

    private static String buildMessage(OffsetDateTime lockedUntil) {
        if (lockedUntil == null) {
            return "관리자 계정이 잠겼습니다. 잠시 후 다시 시도해 주세요.";
        }
        return "관리자 계정이 잠겼습니다. 잠금 해제 시각(UTC): "
                + lockedUntil.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
