package com.bridgework.profile.service;

import com.bridgework.profile.config.BridgeWorkProfileOcrProperties;
import com.bridgework.profile.dto.ProfilePortfolioExtractResponseDto;
import com.bridgework.profile.exception.ProfileOcrDomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfilePortfolioDraftService {

    private final FastApiProfileOcrClient fastApiProfileOcrClient;
    private final BridgeWorkProfileOcrProperties profileOcrProperties;
    private final ObjectMapper objectMapper;

    public ProfilePortfolioDraftService(FastApiProfileOcrClient fastApiProfileOcrClient,
                                        BridgeWorkProfileOcrProperties profileOcrProperties,
                                        ObjectMapper objectMapper) {
        this.fastApiProfileOcrClient = fastApiProfileOcrClient;
        this.profileOcrProperties = profileOcrProperties;
        this.objectMapper = objectMapper;
    }

    public ProfilePortfolioExtractResponseDto extract(MultipartFile file) {
        validateUploadFile(file);
        byte[] payload = toPayload(file);

        Map<String, Object> aiResponse = fastApiProfileOcrClient.extractProfileDraft(
                normalizeFilename(file.getOriginalFilename()),
                file.getContentType(),
                payload
        );

        Object result = aiResponse.get("result");
        if (!(result instanceof Map<?, ?> resultMap)) {
            throw new ProfileOcrDomainException(
                    "FASTAPI_OCR_INVALID_RESPONSE",
                    HttpStatus.BAD_GATEWAY,
                    "FastAPI OCR 응답 형식이 예상과 다릅니다."
            );
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> casted = (Map<String, Object>) resultMap;
        try {
            return objectMapper.convertValue(casted, ProfilePortfolioExtractResponseDto.class);
        } catch (IllegalArgumentException exception) {
            throw new ProfileOcrDomainException(
                    "FASTAPI_OCR_RESPONSE_CONVERT_FAILED",
                    HttpStatus.BAD_GATEWAY,
                    "FastAPI OCR 응답 변환에 실패했습니다."
            );
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ProfileOcrDomainException(
                    "PROFILE_OCR_EMPTY_FILE",
                    HttpStatus.BAD_REQUEST,
                    "업로드 파일이 비어 있습니다."
            );
        }
        if (file.getSize() > profileOcrProperties.getMaxUploadBytes()) {
            throw new ProfileOcrDomainException(
                    "PROFILE_OCR_FILE_TOO_LARGE",
                    HttpStatus.BAD_REQUEST,
                    "PDF 파일 용량 제한을 초과했습니다."
            );
        }

        String contentType = file.getContentType();
        List<String> allowedContentTypes = profileOcrProperties.getAllowedContentTypes();
        if (!StringUtils.hasText(contentType) || !allowedContentTypes.contains(contentType)) {
            throw new ProfileOcrDomainException(
                    "PROFILE_OCR_UNSUPPORTED_MEDIA_TYPE",
                    HttpStatus.BAD_REQUEST,
                    "PDF 파일만 업로드할 수 있습니다."
            );
        }
    }

    private byte[] toPayload(MultipartFile file) {
        try {
            byte[] payload = file.getBytes();
            if (!hasPdfSignature(payload)) {
                throw new ProfileOcrDomainException(
                        "PROFILE_OCR_INVALID_PDF_SIGNATURE",
                        HttpStatus.BAD_REQUEST,
                        "PDF 파일 시그니처가 올바르지 않습니다."
                );
            }
            return payload;
        } catch (ProfileOcrDomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProfileOcrDomainException(
                    "PROFILE_OCR_FILE_READ_FAILED",
                    HttpStatus.BAD_REQUEST,
                    "업로드 파일을 읽을 수 없습니다."
            );
        }
    }

    private boolean hasPdfSignature(byte[] payload) {
        if (payload == null || payload.length < 5) {
            return false;
        }
        return payload[0] == '%'
                && payload[1] == 'P'
                && payload[2] == 'D'
                && payload[3] == 'F'
                && payload[4] == '-';
    }

    private String normalizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "portfolio.pdf";
        }
        return originalFilename;
    }
}
