package com.bridgework.sync.normalized;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PublicDataNormalizedStoreServiceTest {

    @Test
    void closeMissingRecruitments_whenFetchedIdsExist_updatesOnlyMissingIdsWithoutNotInClause() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PublicDataNormalizedStoreService service = new PublicDataNormalizedStoreService(
                jdbcTemplate,
                new ObjectMapper(),
                new NormalizedSourceRegistry(),
                mock(NaverGeocodingService.class),
                new BridgeWorkSyncProperties()
        );
        OffsetDateTime statusChangedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");

        when(jdbcTemplate.query(
                eq("SELECT external_id FROM pd_kepad_recruitment"),
                org.mockito.ArgumentMatchers.<RowMapper<String>>any()
        ))
                .thenReturn(List.of("J-1", "J-MISSING"));
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        int closedCount = service.closeMissingRecruitments(Set.of("J-1"), statusChangedAt);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

        assertThat(closedCount).isEqualTo(1);
        assertThat(sqlCaptor.getValue())
                .contains("external_id IN (:externalIds)")
                .doesNotContain("NOT IN");
        assertThat(paramsCaptor.getValue().getValue("externalIds")).isEqualTo(List.of("J-MISSING"));
        assertThat(paramsCaptor.getValue().getValue("status")).isEqualTo("CLOSED");
        assertThat(paramsCaptor.getValue().getValue("statusChangedAt")).isEqualTo(statusChangedAt);
    }

    @Test
    void closeMissingRecruitments_whenFetchedIdsExceedJdbcParameterLimit_doesNotBindFetchedIdsToSql() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PublicDataNormalizedStoreService service = new PublicDataNormalizedStoreService(
                jdbcTemplate,
                new ObjectMapper(),
                new NormalizedSourceRegistry(),
                mock(NaverGeocodingService.class),
                new BridgeWorkSyncProperties()
        );
        OffsetDateTime statusChangedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        Set<String> fetchedExternalIds = new HashSet<>(100_005);
        for (int index = 0; index < 100_005; index++) {
            fetchedExternalIds.add("J-" + index);
        }

        when(jdbcTemplate.query(
                eq("SELECT external_id FROM pd_kepad_recruitment"),
                org.mockito.ArgumentMatchers.<RowMapper<String>>any()
        ))
                .thenReturn(List.of("J-1", "J-MISSING"));
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        int closedCount = service.closeMissingRecruitments(fetchedExternalIds, statusChangedAt);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

        assertThat(closedCount).isEqualTo(1);
        assertThat(sqlCaptor.getValue())
                .contains("external_id IN (:externalIds)")
                .doesNotContain("NOT IN");
        assertThat(paramsCaptor.getValue().getValue("externalIds")).isEqualTo(List.of("J-MISSING"));
    }
}
