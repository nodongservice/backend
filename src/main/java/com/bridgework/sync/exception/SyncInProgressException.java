package com.bridgework.sync.exception;

import org.springframework.http.HttpStatus;

public class SyncInProgressException extends SyncDomainException {

    public SyncInProgressException() {
        super(
                "SYNC_IN_PROGRESS",
                HttpStatus.CONFLICT,
                "이미 동기화 작업이 진행 중입니다. 잠시 후 다시 시도해 주세요."
        );
    }
}

