package com.bridgework.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PersonalDataMaskingUtilsTest {

    @Test
    void safeRootCauseSummaryRedactsPostgresFailingRowAndContactInformation() {
        RuntimeException rootCause = new RuntimeException("""
                ERROR: null value violates not-null constraint
                Detail: Failing row contains (29, 홍길동, user@example.com, 010-1234-5678, 민감한 장애 정보).
                """);
        IllegalStateException exception = new IllegalStateException("wrapper", rootCause);

        String summary = PersonalDataMaskingUtils.safeRootCauseSummary(exception);

        assertThat(summary).contains("null value violates not-null constraint", "Detail: [REDACTED]");
        assertThat(summary).doesNotContain("홍길동", "user@example.com", "010-1234-5678", "민감한 장애 정보");
    }
}
