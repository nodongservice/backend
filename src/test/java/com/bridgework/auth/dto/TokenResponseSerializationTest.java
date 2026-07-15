package com.bridgework.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.bridgework.admin.auth.dto.AdminLoginResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TokenResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void refreshTokenIsNotExposedInUserResponseJson() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        TokenPairResponseDto response = new TokenPairResponseDto(
                "access-token", "refresh-secret", "Bearer", now.plusMinutes(15), now.plusDays(14)
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("access-token");
        assertThat(json).doesNotContain("refresh-secret");
        assertThat(json).doesNotContain("\"refreshToken\"");
    }

    @Test
    void refreshTokenIsNotExposedInAdminResponseJson() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        AdminLoginResponseDto response = new AdminLoginResponseDto(
                "admin-access", "admin-refresh-secret", "Bearer", now.plusMinutes(15), now.plusDays(14)
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("admin-access");
        assertThat(json).doesNotContain("admin-refresh-secret");
        assertThat(json).doesNotContain("\"refreshToken\"");
    }
}
