package com.bridgework.common.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bridgework.security.personal-data-encryption")
public class PersonalDataEncryptionProperties {

    @NotBlank
    private String secret = "bridgework-dev-personal-data-encryption-secret";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
