package com.bridgework.common.logging;

import java.util.regex.Pattern;

public final class LogSanitizer {

    public static final String REDACTED_VALUE = "[REDACTED]";

    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
            "(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern SENSITIVE_QUERY_PATTERN = Pattern.compile(
            "(?i)([?&](?:code|token|access_token|refresh_token|signupToken|withdrawalCancelToken|serviceKey|apiKey|apikey|key|secret|password)=)[^&\\s]+"
    );
    private static final Pattern BASIC_AUTH_URL_PATTERN = Pattern.compile(
            "(//[^/\\s:@]+:)[^@\\s/]+(@)"
    );
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
    );
    private static final Pattern SENSITIVE_KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b(password|passwd|token|accessToken|refreshToken|signupToken|withdrawalCancelToken|secret|credential|api[-_]?key|serviceKey|session|jwt)\\s*[:=]\\s*([^\\s,;]+)"
    );

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String sanitized = AUTH_HEADER_PATTERN.matcher(value).replaceAll("$1 " + REDACTED_VALUE);
        sanitized = SENSITIVE_QUERY_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED_VALUE);
        sanitized = BASIC_AUTH_URL_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED_VALUE + "$2");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll(REDACTED_VALUE);
        sanitized = SENSITIVE_KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1=" + REDACTED_VALUE);
        return sanitized;
    }

    public static String sanitizeSingleLine(String value) {
        if (value == null) {
            return null;
        }
        return sanitize(value).replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static String summarizeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }

        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }

        return sanitizeSingleLine(message);
    }
}
