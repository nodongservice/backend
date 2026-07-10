package com.bridgework.common.security;

import java.util.regex.Pattern;

public final class PersonalDataMaskingUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Za-z0-9._%+-]{1,64})@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(01[016789])-?(\\d{3,4})-?(\\d{4})");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?i)(bearer\\s+)([A-Za-z0-9._~+/=-]+)");

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
}
