package com.bridgework.common.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PersonalDataEncryptionBackfillRunner implements ApplicationRunner {

    private static final String ENCRYPTED_PREFIX = "enc:v1:";

    private final JdbcTemplate jdbcTemplate;
    private final PersonalDataEncryptionService encryptionService;

    public PersonalDataEncryptionBackfillRunner(JdbcTemplate jdbcTemplate,
                                                PersonalDataEncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill(
                "user_profile_private_details",
                "profile_id",
                List.of(
                        "full_name",
                        "contact_phone",
                        "contact_email_override",
                        "birth_date",
                        "gender_type",
                        "detail_address",
                        "emergency_contact",
                        "home_lat",
                        "home_lng",
                        "home_geocoded_address"
                )
        );
        backfill(
                "user_profile_sensitive_info",
                "profile_id",
                List.of(
                        "required_supports_json",
                        "disability_type",
                        "disability_severity",
                        "disability_registered_yn",
                        "sensitive_info_consent_yn",
                        "disability_description",
                        "assistive_devices",
                        "work_support_requirements"
                )
        );
    }

    private void backfill(String tableName, String idColumn, List<String> encryptedColumns) {
        String sql = "SELECT * FROM " + tableName;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        for (Map<String, Object> row : rows) {
            Map<String, Object> updatedValues = new LinkedHashMap<>();
            for (String column : encryptedColumns) {
                Object raw = row.get(column);
                if (!(raw instanceof String rawValue) || rawValue.isBlank() || rawValue.startsWith(ENCRYPTED_PREFIX)) {
                    continue;
                }
                updatedValues.put(column, encryptionService.encrypt(rawValue));
            }

            if (updatedValues.isEmpty()) {
                continue;
            }

            StringBuilder updateSql = new StringBuilder("UPDATE ")
                    .append(tableName)
                    .append(" SET ");
            Object[] args = new Object[updatedValues.size() + 1];
            int index = 0;
            for (String column : updatedValues.keySet()) {
                if (index > 0) {
                    updateSql.append(", ");
                }
                updateSql.append(column).append(" = ?");
                args[index++] = updatedValues.get(column);
            }
            updateSql.append(" WHERE ").append(idColumn).append(" = ?");
            args[index] = row.get(idColumn);
            jdbcTemplate.update(updateSql.toString(), args);
        }
    }
}
