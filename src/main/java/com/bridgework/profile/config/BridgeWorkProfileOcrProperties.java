package com.bridgework.profile.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bridgework.profile-ocr")
public class BridgeWorkProfileOcrProperties {

    @NotBlank
    private String fastapiBaseUrl = "http://localhost:8000";

    @NotBlank
    private String extractPath = "/api/v1/profile-draft/from-portfolio";

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(120);

    @Min(1)
    private int retryAttemptsPerUri = 2;

    @Min(1)
    private long maxUploadBytes = 10 * 1024 * 1024;

    @NotEmpty
    private List<String> allowedContentTypes = List.of("application/pdf");

    public String getFastapiBaseUrl() {
        return fastapiBaseUrl;
    }

    public void setFastapiBaseUrl(String fastapiBaseUrl) {
        this.fastapiBaseUrl = fastapiBaseUrl;
    }

    public String getExtractPath() {
        return extractPath;
    }

    public void setExtractPath(String extractPath) {
        this.extractPath = extractPath;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public int getRetryAttemptsPerUri() {
        return retryAttemptsPerUri;
    }

    public void setRetryAttemptsPerUri(int retryAttemptsPerUri) {
        this.retryAttemptsPerUri = retryAttemptsPerUri;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

}
