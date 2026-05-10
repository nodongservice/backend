package com.bridgework.profile.service;

import com.bridgework.common.config.BridgeWorkHealthMonitorProperties;
import com.bridgework.profile.config.BridgeWorkProfileOcrProperties;
import com.bridgework.profile.exception.ProfileOcrDomainException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
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
    private final BridgeWorkHealthMonitorProperties healthMonitorProperties;

    public FastApiProfileOcrClient(WebClient webClient,
                                   BridgeWorkProfileOcrProperties profileOcrProperties,
                                   BridgeWorkHealthMonitorProperties healthMonitorProperties) {
        this.webClient = webClient;
        this.profileOcrProperties = profileOcrProperties;
        this.healthMonitorProperties = healthMonitorProperties;
    }

    public Map<String, Object> extractProfileDraft(String filename, String contentType, byte[] payload) {
        List<String> candidateUris = resolveCandidateUris();
        List<String> attemptFailures = new ArrayList<>();

        for (int index = 0; index < candidateUris.size(); index++) {
            String uri = candidateUris.get(index);
            boolean isLastAttempt = index == candidateUris.size() - 1;

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
                String redirectLocation = exception.getHeaders().getFirst("Location");
                String failureMessage = "status=" + exception.getStatusCode().value()
                        + ", uri=" + uri
                        + (StringUtils.hasText(redirectLocation) ? ", location=" + redirectLocation : "")
                        + ", body=" + sanitizeErrorBody(exception.getResponseBodyAsString());
                attemptFailures.add(failureMessage);

                // 3xx/5xx는 내부 포트 폴백을 위해 다음 후보로 재시도한다.
                if (!isLastAttempt && (exception.getStatusCode().is3xxRedirection() || exception.getStatusCode().is5xxServerError())) {
                    continue;
                }

                // 4xx는 즉시 클라이언트 오류로 간주하고 중단한다.
                throw new ProfileOcrDomainException(
                        "FASTAPI_OCR_HTTP_ERROR",
                        HttpStatus.BAD_GATEWAY,
                        "FastAPI OCR 호출 실패: " + failureMessage
                );
            } catch (ProfileOcrDomainException exception) {
                throw exception;
            } catch (Exception exception) {
                attemptFailures.add("uri=" + uri + ", reason=" + exception.getMessage());
                if (!isLastAttempt) {
                    continue;
                }
                throw new ProfileOcrDomainException(
                        "FASTAPI_OCR_CALL_FAILED",
                        HttpStatus.BAD_GATEWAY,
                        "FastAPI OCR 호출 중 오류가 발생했습니다: " + exception.getMessage()
                );
            }
        }

        throw new ProfileOcrDomainException(
                "FASTAPI_OCR_CALL_FAILED",
                HttpStatus.BAD_GATEWAY,
                "FastAPI OCR 호출 실패: " + String.join(" | ", attemptFailures)
        );
    }

    private List<String> resolveCandidateUris() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String extractPath = profileOcrProperties.getExtractPath();
        candidates.add(joinBaseUrlAndPath(profileOcrProperties.getFastapiBaseUrl(), extractPath));

        for (String healthUrl : healthMonitorProperties.getFastapiHealthUrls()) {
            String baseUrl = toBaseUrlFromHealthUrl(healthUrl);
            if (!StringUtils.hasText(baseUrl)) {
                continue;
            }
            candidates.add(joinBaseUrlAndPath(baseUrl, extractPath));
        }

        return List.copyOf(candidates);
    }

    private String toBaseUrlFromHealthUrl(String healthUrl) {
        if (!StringUtils.hasText(healthUrl)) {
            return "";
        }

        String normalized = StringUtils.trimWhitespace(healthUrl);
        int healthPathIndex = normalized.indexOf("/health");
        if (healthPathIndex < 0) {
            return normalized;
        }
        return normalized.substring(0, healthPathIndex);
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

    private static final class InMemoryFileResource extends ByteArrayResource {
        private final String filename;

        private InMemoryFileResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public long contentLength() {
            return getByteArray().length;
        }
    }
}
