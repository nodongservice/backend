package com.bridgework.profile.service;

import com.bridgework.profile.config.BridgeWorkProfileOcrProperties;
import com.bridgework.profile.exception.ProfileOcrDomainException;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class FastApiProfileOcrClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private final WebClient webClient;
    private final BridgeWorkProfileOcrProperties profileOcrProperties;

    public FastApiProfileOcrClient(WebClient webClient, BridgeWorkProfileOcrProperties profileOcrProperties) {
        this.webClient = webClient;
        this.profileOcrProperties = profileOcrProperties;
    }

    public Map<String, Object> extractProfileDraft(String filename, String contentType, byte[] payload) {
        String uri = joinBaseUrlAndPath(profileOcrProperties.getFastapiBaseUrl(), profileOcrProperties.getExtractPath());

        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part(
                "file",
                new InMemoryFileResource(payload, filename),
                MediaType.parseMediaType(contentType == null ? MediaType.APPLICATION_PDF_VALUE : contentType)
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(profileOcrProperties.getRequestTimeout())
                    .block();

            if (response == null) {
                throw new ProfileOcrDomainException(
                        "FASTAPI_OCR_EMPTY_RESPONSE",
                        HttpStatus.BAD_GATEWAY,
                        "FastAPI OCR 응답이 비어 있습니다."
                );
            }
            return response;
        } catch (WebClientResponseException exception) {
            throw new ProfileOcrDomainException(
                    "FASTAPI_OCR_HTTP_ERROR",
                    HttpStatus.BAD_GATEWAY,
                    "FastAPI OCR 호출 실패: status=" + exception.getStatusCode().value()
                            + ", body=" + sanitizeErrorBody(exception.getResponseBodyAsString())
            );
        } catch (ProfileOcrDomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProfileOcrDomainException(
                    "FASTAPI_OCR_CALL_FAILED",
                    HttpStatus.BAD_GATEWAY,
                    "FastAPI OCR 호출 중 오류가 발생했습니다: " + exception.getMessage()
            );
        }
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String joinBaseUrlAndPath(String baseUrl, String path) {
        String normalizedBase = StringUtils.trimWhitespace(baseUrl);
        if (!StringUtils.hasText(normalizedBase)) {
            return normalizePath(path);
        }
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        return normalizedBase + normalizePath(path);
    }

    private String sanitizeErrorBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String sanitized = body.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
    }

    private static final class InMemoryFileResource extends org.springframework.core.io.InputStreamResource {
        private final String filename;

        private InMemoryFileResource(byte[] bytes, String filename) {
            super(new ByteArrayInputStream(bytes));
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
