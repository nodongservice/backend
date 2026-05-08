package com.bridgework.common.exception;

import com.bridgework.common.dto.ApiErrorResponse;
import com.bridgework.sync.exception.SyncDomainException;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SyncDomainException.class)
    public ResponseEntity<ApiErrorResponse> handleSyncDomainException(SyncDomainException exception) {
        return ResponseEntity
                .status(exception.getHttpStatus())
                .body(new ApiErrorResponse(
                        exception.getErrorCode(),
                        exception.getMessage(),
                        OffsetDateTime.now()
                ));
    }

    @ExceptionHandler(BridgeWorkDomainException.class)
    public ResponseEntity<ApiErrorResponse> handleBridgeWorkDomainException(BridgeWorkDomainException exception) {
        return ResponseEntity
                .status(exception.getHttpStatus())
                .body(new ApiErrorResponse(
                        exception.getErrorCode(),
                        exception.getMessage(),
                        OffsetDateTime.now()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldMessage)
                .collect(Collectors.joining(", "));

        if (message.isBlank()) {
            message = "요청 데이터가 유효하지 않습니다.";
        }

        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "VALIDATION_ERROR",
                message,
                OffsetDateTime.now()
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "VALIDATION_ERROR",
                exception.getMessage(),
                OffsetDateTime.now()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        log.warn("요청 본문 파싱 실패", exception);
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "VALIDATION_ERROR",
                "요청 본문 JSON 형식이 올바르지 않습니다.",
                OffsetDateTime.now()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("처리되지 않은 예외 발생", exception);
        return ResponseEntity.internalServerError().body(new ApiErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "내부 서버 오류가 발생했습니다.",
                OffsetDateTime.now()
        ));
    }

    private String toFieldMessage(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
