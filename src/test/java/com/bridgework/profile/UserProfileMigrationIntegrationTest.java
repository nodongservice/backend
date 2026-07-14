package com.bridgework.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class UserProfileMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("bridgework_test")
            .withUsername("bridgework")
            .withPassword("bridgework");

    @BeforeAll
    static void migrateSchema() throws Exception {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void legacySensitiveColumnsAllowNewSeparatedProfileWrites() throws Exception {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            assertThat(columnIsNullable(statement, "user_profile", "required_supports_json")).isTrue();
            assertThat(columnIsNullable(statement, "user_profile", "disability_type")).isTrue();
        }
    }

    @Test
    void userProfileHasOptimisticLockVersionColumn() throws Exception {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT data_type, is_nullable, column_default
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'user_profile'
                      AND column_name = 'version'
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("data_type")).isEqualTo("bigint");
                assertThat(resultSet.getString("is_nullable")).isEqualTo("NO");
                assertThat(resultSet.getString("column_default")).isEqualTo("0");
            }
        }
    }

    private static boolean columnIsNullable(Statement statement, String table, String column) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = '%s'
                  AND column_name = '%s'
                """.formatted(table, column))) {
            return resultSet.next() && "YES".equals(resultSet.getString("is_nullable"));
        }
    }

    private static Connection openConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
