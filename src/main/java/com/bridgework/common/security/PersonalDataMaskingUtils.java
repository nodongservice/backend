package com.bridgework.common.security;

import java.util.regex.Pattern;

public final class PersonalDataMaskingUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Za-z0-9._%+-]{1,64})@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(01[016789])-?(\\d{3,4})-?(\\d{4})");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?i)(bearer\\s+)([A-Za-z0-9._~+/=-]+)");
    private static final Pattern POSTGRES_FAILING_ROW_PATTERN = Pattern.compile(
            "(?is)(detail:\\s*)?failing row contains\\s*\\(.*"
    );

    private PersonalDataMaskingUtils() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(Math.max(atIndex, 0));
        }
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        return PHONE_PATTERN.matcher(phone).replaceAll("$1-****-$3");
    }

    public static String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String sanitized = EMAIL_PATTERN.matcher(text).replaceAll(matchResult -> maskEmail(matchResult.group()));
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("$1-****-$3");
        sanitized = TOKEN_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
        return sanitized;
    }

    public static String sanitizeExceptionMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String withoutFailingRow = POSTGRES_FAILING_ROW_PATTERN.matcher(message)
                .replaceFirst("Detail: [REDACTED]");
        return sanitizeText(withoutFailingRow).replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static String safeRootCauseSummary(Throwable throwable) {
        if (throwable == null) {
            return "(원인 없음)";
        }

        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String message = sanitizeExceptionMessage(rootCause.getMessage());
        if (message == null || message.isBlank()) {
            return rootCause.getClass().getSimpleName();
        }
        return rootCause.getClass().getSimpleName() + ": " + message;
    }
}
