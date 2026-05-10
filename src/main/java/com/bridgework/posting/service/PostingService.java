package com.bridgework.posting.service;

import com.bridgework.posting.dto.PostingDetailDto;
import com.bridgework.posting.dto.PostingListItemDto;
import com.bridgework.posting.dto.ScrapCommandResponseDto;
import com.bridgework.posting.entity.JobScrap;
import com.bridgework.posting.exception.PostingDomainException;
import com.bridgework.posting.repository.JobScrapRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostingService {

    private static final int MAX_POPULAR_LIMIT = 100;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JobScrapRepository jobScrapRepository;

    public PostingService(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                          JobScrapRepository jobScrapRepository) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.jobScrapRepository = jobScrapRepository;
    }

    @Transactional(readOnly = true)
    public List<PostingListItemDto> getPopularPostings(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_POPULAR_LIMIT));
        String sql = """
                SELECT p.id AS posting_id,
                       p.external_id,
                       p.buspla_name,
                       p.job_nm,
                       p.comp_addr,
                       p.emp_type,
                       p.salary_type,
                       p.salary,
                       p.term_date,
                       COALESCE(p.offerreg_dt, p.reg_dt) AS registered_at,
                       p.posting_status,
                       COALESCE(COUNT(s.id), 0) AS scrap_count
                FROM pd_kepad_recruitment p
                LEFT JOIN job_scrap s ON s.posting_id = p.id
                WHERE p.posting_status = 'ACTIVE'
                GROUP BY p.id
                ORDER BY scrap_count DESC,
                         COALESCE(p.offerreg_dt, p.reg_dt) DESC NULLS LAST,
                         p.id DESC
                LIMIT :limit
                """;

        try {
            return namedParameterJdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource("limit", safeLimit),
                    (resultSet, rowNum) -> toPostingListItem(resultSet, null)
            );
        } catch (DataAccessException exception) {
            throw new PostingDomainException(
                    "POPULAR_POSTING_QUERY_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "인기 공고 조회에 실패했습니다.",
                    exception
            );
        }
    }

    @Transactional(readOnly = true)
    public PostingDetailDto getPostingDetail(Long postingId, Long userId) {
        String sql = """
                SELECT p.id AS posting_id,
                       p.external_id,
                       p.buspla_name,
                       p.job_nm,
                       p.comp_addr,
                       p.cntct_no,
                       p.emp_type,
                       p.enter_type,
                       p.salary_type,
                       p.salary,
                       p.term_date,
                       p.offerreg_dt,
                       p.reg_dt,
                       p.req_career,
                       p.req_educ,
                       p.req_major,
                       p.req_licens,
                       p.regagn_name,
                       p.geo_latitude,
                       p.geo_longitude,
                       p.posting_status,
                       COALESCE(scrap_stats.scrap_count, 0) AS scrap_count,
                       CASE WHEN :userId IS NULL THEN FALSE
                            WHEN my_scrap.id IS NULL THEN FALSE
                            ELSE TRUE END AS scrapped_by_me
                FROM pd_kepad_recruitment p
                LEFT JOIN (
                    SELECT posting_id, COUNT(*) AS scrap_count
                    FROM job_scrap
                    GROUP BY posting_id
                ) scrap_stats ON scrap_stats.posting_id = p.id
                LEFT JOIN job_scrap my_scrap
                       ON my_scrap.posting_id = p.id
                      AND my_scrap.user_id = :userId
                WHERE p.id = :postingId
                """;

        try {
            PostingDetailDto detail = namedParameterJdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("postingId", postingId)
                            .addValue("userId", userId),
                    (resultSet) -> resultSet.next() ? toPostingDetail(resultSet) : null
            );

            if (detail == null) {
                throw new PostingDomainException(
                        "POSTING_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "공고를 찾을 수 없습니다."
                );
            }
            return detail;
        } catch (PostingDomainException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new PostingDomainException(
                    "POSTING_DETAIL_QUERY_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "공고 상세 조회에 실패했습니다.",
                    exception
            );
        }
    }

    @Transactional(readOnly = true)
    public List<PostingListItemDto> getMyScrappedPostings(Long userId) {
        String sql = """
                SELECT p.id AS posting_id,
                       p.external_id,
                       p.buspla_name,
                       p.job_nm,
                       p.comp_addr,
                       p.emp_type,
                       p.salary_type,
                       p.salary,
                       p.term_date,
                       COALESCE(p.offerreg_dt, p.reg_dt) AS registered_at,
                       p.posting_status,
                       COALESCE(scrap_stats.scrap_count, 0) AS scrap_count,
                       s.created_at AS scrapped_at
                FROM job_scrap s
                INNER JOIN pd_kepad_recruitment p ON p.id = s.posting_id
                LEFT JOIN (
                    SELECT posting_id, COUNT(*) AS scrap_count
                    FROM job_scrap
                    GROUP BY posting_id
                ) scrap_stats ON scrap_stats.posting_id = p.id
                WHERE s.user_id = :userId
                ORDER BY s.created_at DESC, s.id DESC
                """;

        try {
            return namedParameterJdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource("userId", userId),
                    (resultSet, rowNum) -> toPostingListItem(resultSet, resultSet.getObject("scrapped_at", OffsetDateTime.class))
            );
        } catch (DataAccessException exception) {
            throw new PostingDomainException(
                    "SCRAP_LIST_QUERY_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "스크랩 공고 목록 조회에 실패했습니다.",
                    exception
            );
        }
    }

    @Transactional
    public ScrapCommandResponseDto scrapPosting(Long userId, Long postingId) {
        assertPostingCanBeScrapped(postingId);
        if (!jobScrapRepository.existsByUserIdAndPostingId(userId, postingId)) {
            JobScrap jobScrap = new JobScrap();
            jobScrap.setUserId(userId);
            jobScrap.setPostingId(postingId);
            jobScrapRepository.save(jobScrap);
        }
        return new ScrapCommandResponseDto(postingId, true);
    }

    @Transactional
    public ScrapCommandResponseDto deleteScrap(Long userId, Long postingId) {
        jobScrapRepository.deleteByUserIdAndPostingId(userId, postingId);
        return new ScrapCommandResponseDto(postingId, false);
    }

    private void assertPostingCanBeScrapped(Long postingId) {
        String sql = """
                SELECT posting_status
                FROM pd_kepad_recruitment
                WHERE id = :postingId
                """;

        String status = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("postingId", postingId),
                resultSet -> resultSet.next() ? resultSet.getString("posting_status") : null
        );

        if (status == null) {
            throw new PostingDomainException(
                    "POSTING_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "공고를 찾을 수 없습니다."
            );
        }

        if (!"ACTIVE".equals(status)) {
            throw new PostingDomainException(
                    "POSTING_NOT_ACTIVE",
                    HttpStatus.CONFLICT,
                    "마감된 공고는 스크랩할 수 없습니다."
            );
        }
    }

    private PostingListItemDto toPostingListItem(ResultSet resultSet, OffsetDateTime scrappedAt) throws SQLException {
        return new PostingListItemDto(
                resultSet.getLong("posting_id"),
                resultSet.getString("external_id"),
                resultSet.getString("buspla_name"),
                resultSet.getString("job_nm"),
                resultSet.getString("comp_addr"),
                resultSet.getString("emp_type"),
                resultSet.getString("salary_type"),
                resultSet.getString("salary"),
                resultSet.getString("term_date"),
                resultSet.getString("registered_at"),
                resultSet.getString("posting_status"),
                resultSet.getLong("scrap_count"),
                scrappedAt
        );
    }

    private PostingDetailDto toPostingDetail(ResultSet resultSet) throws SQLException {
        return new PostingDetailDto(
                resultSet.getLong("posting_id"),
                resultSet.getString("external_id"),
                resultSet.getString("buspla_name"),
                resultSet.getString("job_nm"),
                resultSet.getString("comp_addr"),
                resultSet.getString("cntct_no"),
                resultSet.getString("emp_type"),
                resultSet.getString("enter_type"),
                resultSet.getString("salary_type"),
                resultSet.getString("salary"),
                resultSet.getString("term_date"),
                resultSet.getString("offerreg_dt"),
                resultSet.getString("reg_dt"),
                resultSet.getString("req_career"),
                resultSet.getString("req_educ"),
                resultSet.getString("req_major"),
                resultSet.getString("req_licens"),
                resultSet.getString("regagn_name"),
                resultSet.getObject("geo_latitude", Double.class),
                resultSet.getObject("geo_longitude", Double.class),
                resultSet.getString("posting_status"),
                resultSet.getLong("scrap_count"),
                resultSet.getBoolean("scrapped_by_me")
        );
    }
}
