package com.bridgework.recommend.service;

import com.bridgework.recommend.dto.RecommendJobResponseDto;
import com.bridgework.recommend.exception.RecommendDomainException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RecommendJobQueryService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public RecommendJobQueryService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<RecommendJobResponseDto> getLatestRecruitments() {
        String sql = """
                SELECT external_id,
                       buspla_name,
                       job_nm,
                       comp_addr,
                       emp_type,
                       enter_type,
                       salary_type,
                       salary,
                       term_date,
                       offerreg_dt,
                       reg_dt,
                       req_career,
                       req_educ,
                       req_major,
                       req_licens,
                       geo_latitude,
                       geo_longitude
                FROM pd_kepad_recruitment
                ORDER BY reg_dt DESC NULLS LAST, updated_at DESC, external_id ASC
                """;
        try {
            return namedParameterJdbcTemplate.query(sql, (rs, rowNum) -> new RecommendJobResponseDto(
                    rs.getString("external_id"),
                    rs.getString("buspla_name"),
                    rs.getString("job_nm"),
                    rs.getString("comp_addr"),
                    rs.getString("emp_type"),
                    rs.getString("enter_type"),
                    rs.getString("salary_type"),
                    rs.getString("salary"),
                    rs.getString("term_date"),
                    rs.getString("offerreg_dt"),
                    rs.getString("reg_dt"),
                    rs.getString("req_career"),
                    rs.getString("req_educ"),
                    rs.getString("req_major"),
                    rs.getString("req_licens"),
                    rs.getObject("geo_latitude", Double.class),
                    rs.getObject("geo_longitude", Double.class)
            ));
        } catch (DataAccessException exception) {
            throw new RecommendDomainException(
                    "RECOMMEND_QUERY_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "추천 공고 조회에 실패했습니다. DB 스키마와 동기화 상태를 확인하세요.",
                    exception
            );
        }
    }
}

