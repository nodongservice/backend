package com.bridgework.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bridgework.recommend.dto.RecommendJobResponseDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SuppressWarnings({"unchecked", "rawtypes"})
class RecommendJobQueryServiceTest {

    @Test
    void getLatestRecruitments_filtersActiveOpenRecruitmentsAndPrioritizesGeocodedRows() {
        NamedParameterJdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class))).thenReturn(List.<RecommendJobResponseDto>of());

        RecommendJobQueryService service = new RecommendJobQueryService(jdbcTemplate);

        service.getLatestRecruitments();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("posting_status = 'ACTIVE' OR posting_status IS NULL");
        assertThat(sql).contains("job_nm IS NOT NULL");
        assertThat(sql).contains("buspla_name IS NOT NULL");
        assertThat(sql).contains("RIGHT(REGEXP_REPLACE(COALESCE(term_date, ''), '[^0-9]', '', 'g'), 8) >= TO_CHAR(CURRENT_DATE, 'YYYYMMDD')");
        assertThat(sql).contains("WHEN geo_latitude IS NOT NULL AND geo_longitude IS NOT NULL THEN 0");
    }
}
