package com.bridgework.sync.exception;

import org.springframework.http.HttpStatus;

public class PublicDataRecordNotFoundException extends SyncDomainException {

    public PublicDataRecordNotFoundException(Long recordId) {
        super(
                "PUBLIC_DATA_RECORD_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                "공공데이터 레코드를 찾을 수 없습니다. recordId=" + recordId
        );
    }
}
