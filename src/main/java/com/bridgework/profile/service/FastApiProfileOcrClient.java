package com.bridgework.profile.service;

import com.bridgework.common.config.BridgeWorkHealthMonitorProperties;
import com.bridgework.profile.config.BridgeWorkProfileOcrProperties;
import com.bridgework.profile.exception.ProfileOcrDomainException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class FastApiProfileOcrClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };
    private static final List<String> RETRYABLE_MESSAGE_KEYWORDS = List.of(
            "connection prematurely closed before response",
            "connection reset by peer",
            "broken pipe"
    );

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
        int maxAttemptsPerUri = Math.max(1, profileOcrProperties.getRetryAttemptsPerUri());

        for (int index = 0; index < candidateUris.size(); index++) {
            String uri = candidateUris.get(index);
            boolean hasNextUri = index < candidateUris.size() - 1;

            for (int attempt = 1; attempt <= maxAttemptsPerUri; attempt++) {
                boolean hasNextAttempt = attempt < maxAttemptsPerUri;

                MultipartBodyBuilder multipartBodyBuilder = buildMultipartBody(filename, contentType, payload);
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
                            + ", attempt=" + attempt
                            + (StringUtils.hasText(redirectLocation) ? ", location=" + redirectLocation : "")
                            + ", body=" + sanitizeErrorBody(exception.getResponseBodyAsString());
                    attemptFailures.add(failureMessage);

                    boolean retryableHttpStatus = isRetryableHttpStatus(exception.getStatusCode());
                    if (retryableHttpStatus && hasNextAttempt) {
                        continue;
                    }
                    if (retryableHttpStatus && hasNextUri) {
                        break;
                    }

                    throw new ProfileOcrDomainException(
                            "FASTAPI_OCR_HTTP_ERROR",
                            HttpStatus.BAD_GATEWAY,
                            "FastAPI OCR 호출 실패: " + failureMessage
                    );
                } catch (ProfileOcrDomainException exception) {
                    throw exception;
                } catch (Exception exception) {
                    String failureMessage = "uri=" + uri
                            + ", attempt=" + attempt
                            + ", reason=" + summarizeException(exception);
                    attemptFailures.add(failureMessage);

                    boolean retryableConnectionException = isRetryableConnectionException(exception);
                    if (retryableConnectionException && hasNextAttempt) {
                        continue;
                    }
                    if (retryableConnectionException && hasNextUri) {
                        break;
                    }

                    throw new ProfileOcrDomainException(
                            "FASTAPI_OCR_CALL_FAILED",
                            HttpStatus.BAD_GATEWAY,
                            "FastAPI OCR 호출 중 오류가 발생했습니다: " + summarizeException(exception)
                    );
                }
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

        for (String healthUrl : healthMonitorProperties.getFastapiHealthUrls()) {
            String baseUrl = toBaseUrlFromHealthUrl(healthUrl);
            if (!StringUtils.hasText(baseUrl)) {
                continue;
            }
            candidates.add(joinBaseUrlAndPath(baseUrl, profileOcrProperties.getExtractPath()));
        }

        candidates.add(joinBaseUrlAndPath(profileOcrProperties.getFastapiBaseUrl(), profileOcrProperties.getExtractPath()));
        return List.copyOf(candidates);
    }

    private MultipartBodyBuilder buildMultipartBody(String filename, String contentType, byte[] payload) {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part(
                "file",
                new InMemoryFileResource(payload, filename),
                resolveContentType(contentType)
        );
        return multipartBodyBuilder;
    }

    private MediaType resolveContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_PDF;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_PDF;
        }
    }

    private boolean isRetryableHttpStatus(HttpStatusCode statusCode) {
        int status = statusCode.value();
        return (status >= 300 && status < 400) || status >= 500;
    }

    private boolean isRetryableConnectionException(Exception exception) {
        if (exception instanceof WebClientRequestException || hasCauseOfType(exception, TimeoutException.class)) {
            return true;
        }

        for (String keyword : RETRYABLE_MESSAGE_KEYWORDS) {
            if (containsKeywordInCauseChain(exception, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCauseOfType(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsKeywordInCauseChain(Throwable throwable, String keyword) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (StringUtils.hasText(message) && message.toLowerCase().contains(keyword)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String summarizeException(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getMessage();
        }
        if (!StringUtils.hasText(message)) {
            return rootCause.getClass().getSimpleName();
        }
        return rootCause.getClass().getSimpleName() + ": " + message;
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
