package com.bridgework.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void sanitizeRedactsSensitiveTokensInFreeText() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature";
        String value = "Authorization: Bearer access-token "
                + "https://user:pass@example.com/callback?code=oauth-code&serviceKey=public-key "
                + "refreshToken=refresh-token " + jwt;

        String sanitized = LogSanitizer.sanitizeSingleLine(value);

        assertThat(sanitized).contains("Bearer [REDACTED]");
        assertThat(sanitized).contains("code=[REDACTED]");
        assertThat(sanitized).contains("serviceKey=[REDACTED]");
        assertThat(sanitized).contains("refreshToken=[REDACTED]");
        assertThat(sanitized).doesNotContain("access-token", "oauth-code", "public-key", "refresh-token", "pass", jwt);
    }

    @Test
    void summarizeThrowableUsesRootCauseAndSanitizesIt() {
        RuntimeException exception = new RuntimeException(
                "wrapper",
                new IllegalArgumentException("token=secret-token\nfailed")
        );

        assertThat(LogSanitizer.summarizeThrowable(exception))
                .isEqualTo("token=[REDACTED] failed");
    }
}
