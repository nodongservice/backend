package com.bridgework.common.exception;

import com.bridgework.common.dto.ApiResponse;
import com.bridgework.common.notification.DiscordNotifierService;
import com.bridgework.common.security.PersonalDataMaskingUtils;
import com.bridgework.sync.exception.SyncDomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
        log.warn("요청 본문 파싱 실패: {}", PersonalDataMaskingUtils.sanitizeText(exception.getMostSpecificCause().getMessage()));
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "요청 본문 JSON 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
        log.warn("업로드 파일 크기 제한 초과: {}", PersonalDataMaskingUtils.sanitizeText(exception.getMessage()));
        return ResponseEntity
                .status(413)
                .body(ApiResponse.error("FILE_TOO_LARGE", "업로드 파일 용량 제한을 초과했습니다."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.warn("데이터 무결성 충돌: uri={}, cause={}",
                request == null ? "(unknown)" : request.getRequestURI(),
                PersonalDataMaskingUtils.safeRootCauseSummary(exception));
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DATA_CONFLICT", "이미 처리되었거나 현재 상태와 충돌하는 요청입니다."));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLockingFailureException(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        log.warn("동시 수정 충돌: uri={}, cause={}",
                request == null ? "(unknown)" : request.getRequestURI(),
                PersonalDataMaskingUtils.safeRootCauseSummary(exception));
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONCURRENT_MODIFICATION", "다른 요청에서 먼저 변경되었습니다. 최신 내용을 확인한 뒤 다시 시도해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        logSafeServerException("처리되지 않은 예외", exception, request);
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNAL_SERVER_ERROR", "내부 서버 오류가 발생했습니다."));
    }

    private void logSafeServerException(String title, Exception exception, HttpServletRequest request) {
        String requestUri = request == null ? "(unknown)" : request.getRequestURI();
        String safeSummary = PersonalDataMaskingUtils.safeRootCauseSummary(exception);
        log.error("{}: uri={}, cause={}", title, requestUri, safeSummary);
        try {
            discordNotifierService.notifyUnhandledException(
                    requestUri,
                    "INTERNAL_SERVER_ERROR",
                    "처리되지 않은 예외가 발생했습니다.",
                    exception
            );
        } catch (Exception notifyException) {
            log.warn("예외 알림 전송 실패: {}", PersonalDataMaskingUtils.safeRootCauseSummary(notifyException));
        }
    }

    private String toFieldMessage(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
