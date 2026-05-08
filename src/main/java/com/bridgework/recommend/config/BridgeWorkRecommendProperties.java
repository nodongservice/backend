package com.bridgework.recommend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bridgework.recommend")
public class BridgeWorkRecommendProperties {

    @NotBlank
    private String fastapiBaseUrl = "http://localhost:8000";

    @NotBlank
    private String quickPath = "/api/v1/score/quick";

    @NotBlank
    private String mapPath = "/api/v1/score/map";

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(20);

    public String getFastapiBaseUrl() {
        return fastapiBaseUrl;
    }

    public void setFastapiBaseUrl(String fastapiBaseUrl) {
        this.fastapiBaseUrl = fastapiBaseUrl;
    }

    public String getQuickPath() {
        return quickPath;
    }

    public void setQuickPath(String quickPath) {
        this.quickPath = quickPath;
    }

    public String getMapPath() {
        return mapPath;
    }

    public void setMapPath(String mapPath) {
        this.mapPath = mapPath;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}

