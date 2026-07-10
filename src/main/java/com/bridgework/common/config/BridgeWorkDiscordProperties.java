package com.bridgework.common.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bridgework.discord")
public class BridgeWorkDiscordProperties {

    @NotNull
    private String springBotWebhookUrl = "";
    @NotNull
    private String accessibilityFeedbackWebhookUrl = "";

    public String getSpringBotWebhookUrl() {
        return springBotWebhookUrl;
    }

    public void setSpringBotWebhookUrl(String springBotWebhookUrl) {
        this.springBotWebhookUrl = springBotWebhookUrl;
    }

    public String getAccessibilityFeedbackWebhookUrl() {
        return accessibilityFeedbackWebhookUrl;
    }

    public void setAccessibilityFeedbackWebhookUrl(String accessibilityFeedbackWebhookUrl) {
        this.accessibilityFeedbackWebhookUrl = accessibilityFeedbackWebhookUrl;
    }
}
