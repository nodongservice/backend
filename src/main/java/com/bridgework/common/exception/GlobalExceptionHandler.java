package com.bridgework.common.exception;

import com.bridgework.common.dto.ApiResponse;
import com.bridgework.common.notification.DiscordNotifierService;
import com.bridgework.sync.exception.SyncDomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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
    private final DiscordNotifierService discordNotifierService;

    public GlobalExceptionHandler(DiscordNotifierService discordNotifierService) {
        this.discordNotifierService = discordNotifierService;
    }

    @ExceptionHandler(SyncDomainException.class)
    public ResponseEntity<ApiResponse<Object>> handleSyncDomainException(SyncDomainException exception) {
        return ResponseEntity
                .status(exception.getHttpStatus())
                .body(ApiResponse.error(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(BridgeWorkDomainException.class)
    public ResponseEntity<ApiResponse<Object>> handleBridgeWorkDomainException(BridgeWorkDomainException exception) {
        return ResponseEntity
                .status(exception.getHttpStatus())
                .body(ApiResponse.error(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldMessage)
                .collect(Collectors.joining(", "));

        if (message.isBlank()) {
            message = "요청 데이터가 유효하지 않습니다.";
        }

        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        log.warn("요청 본문 파싱 실패", exception);
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "요청 본문 JSON 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("처리되지 않은 예외 발생", exception);
        try {
            String requestUri = request == null ? null : request.getRequestURI();
            discordNotifierService.notifyUnhandledException(
                    requestUri,
                    "INTERNAL_SERVER_ERROR",
                    "처리되지 않은 예외가 발생했습니다.",
                    exception
            );
        } catch (Exception notifyException) {
            log.warn("예외 알림 전송 실패", notifyException);
        }
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNAL_SERVER_ERROR", "내부 서버 오류가 발생했습니다."));
    }

    private String toFieldMessage(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
