package com.bridgework.sync.exception;

import com.bridgework.sync.entity.PublicDataSourceType;
import org.springframework.http.HttpStatus;

public class SyncSourceDisabledException extends SyncDomainException {

    public SyncSourceDisabledException(PublicDataSourceType sourceType) {
        super("SYNC_SOURCE_DISABLED", HttpStatus.BAD_REQUEST, "동기화 소스가 비활성화되어 있습니다: " + sourceType);
    }
}
