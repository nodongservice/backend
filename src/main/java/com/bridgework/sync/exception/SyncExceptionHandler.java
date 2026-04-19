package com.bridgework.sync.exception;

import com.bridgework.sync.dto.ErrorResponseDto;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SyncExceptionHandler {

    @ExceptionHandler(SyncDomainException.class)
    public ResponseEntity<ErrorResponseDto> handleSyncDomainException(SyncDomainException exception) {
        return ResponseEntity
                .status(exception.getHttpStatus())
                .body(new ErrorResponseDto(
                        exception.getErrorCode(),
                        exception.getMessage(),
                        OffsetDateTime.now()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception exception) {
        return ResponseEntity.internalServerError().body(new ErrorResponseDto(
                "INTERNAL_SERVER_ERROR",
                "내부 서버 오류가 발생했습니다.",
                OffsetDateTime.now()
        ));
    }
}
