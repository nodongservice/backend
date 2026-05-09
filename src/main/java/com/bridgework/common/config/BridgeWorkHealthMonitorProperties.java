package com.bridgework.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bridgework.health-monitor")
public class BridgeWorkHealthMonitorProperties {

    private boolean enabled = true;

    @NotNull
    private Duration interval = Duration.ofMinutes(1);

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(5);

    @NotBlank
    private String fastapiHealthUrl = "http://localhost:8000/health";

    @NotBlank
    private String fastapiDbHealthUrl = "http://localhost:8000/db-health";

    @NotNull
    private Duration alertReminderInterval = Duration.ofMinutes(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getFastapiHealthUrl() {
        return fastapiHealthUrl;
    }

    public void setFastapiHealthUrl(String fastapiHealthUrl) {
        this.fastapiHealthUrl = fastapiHealthUrl;
    }

    public String getFastapiDbHealthUrl() {
        return fastapiDbHealthUrl;
    }

    public void setFastapiDbHealthUrl(String fastapiDbHealthUrl) {
        this.fastapiDbHealthUrl = fastapiDbHealthUrl;
    }

    public Duration getAlertReminderInterval() {
        return alertReminderInterval;
    }

    public void setAlertReminderInterval(Duration alertReminderInterval) {
        this.alertReminderInterval = alertReminderInterval;
    }
}
