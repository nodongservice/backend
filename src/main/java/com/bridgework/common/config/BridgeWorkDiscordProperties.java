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
    private String infraAlertWebhookUrl = "";

    public String getSpringBotWebhookUrl() {
        return springBotWebhookUrl;
    }

    public void setSpringBotWebhookUrl(String springBotWebhookUrl) {
        this.springBotWebhookUrl = springBotWebhookUrl;
    }

    public String getInfraAlertWebhookUrl() {
        return infraAlertWebhookUrl;
    }

    public void setInfraAlertWebhookUrl(String infraAlertWebhookUrl) {
        this.infraAlertWebhookUrl = infraAlertWebhookUrl;
    }
}
