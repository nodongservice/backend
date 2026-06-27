package com.bridgework.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bridgework.recommend.dto.RecommendJobResponseDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SuppressWarnings({"unchecked", "rawtypes"})
class RecommendJobQueryServiceTest {

    @Test
    void getLatestRecruitments_filtersActiveOpenRecruitmentsAndPrioritizesGeocodedRows() {
        NamedParameterJdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.<RecommendJobResponseDto>of());

        RecommendJobQueryService service = new RecommendJobQueryService(jdbcTemplate);

        service.getLatestRecruitments(20, 40);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        org.mockito.Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(), parameterCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("posting_status = 'ACTIVE' OR posting_status IS NULL");
        assertThat(sql).contains("job_nm IS NOT NULL");
        assertThat(sql).contains("buspla_name IS NOT NULL");
        assertThat(sql).contains("RIGHT(REGEXP_REPLACE(COALESCE(term_date, ''), '[^0-9]', '', 'g'), 8) >= TO_CHAR(CURRENT_DATE, 'YYYYMMDD')");
        assertThat(sql).contains("WHEN geo_latitude IS NOT NULL AND geo_longitude IS NOT NULL THEN 0");
        assertThat(sql).contains("LIMIT :limit OFFSET :offset");
        assertThat(parameterCaptor.getValue().getValue("limit")).isEqualTo(20);
        assertThat(parameterCaptor.getValue().getValue("offset")).isEqualTo(40);
    }

    @Test
    void countLatestRecruitments_usesSameOpenRecruitmentFilters() {
        NamedParameterJdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.eq(Integer.class)))
                .thenReturn(137);

        RecommendJobQueryService service = new RecommendJobQueryService(jdbcTemplate);

        int count = service.countLatestRecruitments();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.eq(Integer.class));

        String sql = sqlCaptor.getValue();
        assertThat(count).isEqualTo(137);
        assertThat(sql).contains("SELECT COUNT(*)");
        assertThat(sql).contains("posting_status = 'ACTIVE' OR posting_status IS NULL");
        assertThat(sql).contains("job_nm IS NOT NULL");
        assertThat(sql).contains("buspla_name IS NOT NULL");
        assertThat(sql).contains("RIGHT(REGEXP_REPLACE(COALESCE(term_date, ''), '[^0-9]', '', 'g'), 8) >= TO_CHAR(CURRENT_DATE, 'YYYYMMDD')");
    }
}
