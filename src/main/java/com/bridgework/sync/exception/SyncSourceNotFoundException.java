package com.bridgework.sync.exception;

import com.bridgework.sync.entity.PublicDataSourceType;
import org.springframework.http.HttpStatus;

public class SyncSourceNotFoundException extends SyncDomainException {

    public SyncSourceNotFoundException(PublicDataSourceType sourceType) {
        super("SYNC_SOURCE_NOT_FOUND", HttpStatus.BAD_REQUEST, "동기화 소스 설정을 찾을 수 없습니다: " + sourceType);
    }
}
