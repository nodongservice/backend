package com.bridgework.options.service;

import com.bridgework.options.dto.JobCategoryTreeNodeDto;
import com.bridgework.options.dto.OptionItemDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OptionQueryService {

    private static final List<OptionItemDto> REGION_OPTIONS = List.of(
            new OptionItemDto("서울", "서울"),
            new OptionItemDto("부산", "부산"),
            new OptionItemDto("대구", "대구"),
            new OptionItemDto("인천", "인천"),
            new OptionItemDto("광주", "광주"),
            new OptionItemDto("대전", "대전"),
            new OptionItemDto("울산", "울산"),
            new OptionItemDto("세종", "세종"),
            new OptionItemDto("경기", "경기"),
            new OptionItemDto("강원", "강원"),
            new OptionItemDto("충북", "충북"),
            new OptionItemDto("충남", "충남"),
            new OptionItemDto("전북", "전북"),
            new OptionItemDto("전남", "전남"),
            new OptionItemDto("경북", "경북"),
            new OptionItemDto("경남", "경남"),
            new OptionItemDto("제주", "제주")
    );

    private static final List<OptionItemDto> EMPLOYMENT_TYPE_OPTIONS = List.of(
            new OptionItemDto("정규직", "정규직"),
            new OptionItemDto("계약직", "계약직"),
            new OptionItemDto("무기계약직", "무기계약직"),
            new OptionItemDto("시간제", "시간제"),
            new OptionItemDto("일용직", "일용직"),
            new OptionItemDto("인턴", "인턴"),
            new OptionItemDto("파견/용역", "파견/용역"),
            new OptionItemDto("재택/원격", "재택/원격")
    );

    private static final List<OptionItemDto> SALARY_TYPE_OPTIONS = List.of(
            new OptionItemDto("월급", "월급"),
            new OptionItemDto("연봉", "연봉"),
            new OptionItemDto("시급", "시급"),
            new OptionItemDto("일급", "일급"),
            new OptionItemDto("건별/성과급", "건별/성과급"),
            new OptionItemDto("회사 내규에 따름", "회사 내규에 따름"),
            new OptionItemDto("면접 후 협의", "면접 후 협의")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public OptionQueryService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<JobCategoryTreeNodeDto> getJobCategoryTree() {
        String sql = """
                SELECT DISTINCT job_cd, job_cd_nm, job_cd_level
                FROM pd_kepad_job_category
                WHERE job_cd IS NOT NULL
                  AND job_cd_nm IS NOT NULL
                """;

        List<JobCategoryRaw> rows = namedParameterJdbcTemplate.query(
                sql,
                (rs, rowNum) -> new JobCategoryRaw(
                        rs.getString("job_cd"),
                        rs.getString("job_cd_nm"),
                        parseLevel(rs.getString("job_cd_level"))
                )
        );

        rows = rows.stream()
                .sorted(Comparator
                        .comparing((JobCategoryRaw raw) -> raw.level() == null ? Integer.MAX_VALUE : raw.level())
                        .thenComparing(JobCategoryRaw::code))
                .toList();

        Map<String, JobCategoryTreeNodeDto> nodeByCode = new LinkedHashMap<>();
        List<JobCategoryTreeNodeDto> roots = new ArrayList<>();

        for (JobCategoryRaw row : rows) {
            JobCategoryTreeNodeDto node = nodeByCode.computeIfAbsent(
                    row.code(),
                    key -> new JobCategoryTreeNodeDto(row.code(), row.name(), row.level())
            );

            JobCategoryTreeNodeDto parent = findParentNode(nodeByCode, row);
            if (parent == null) {
                if (!roots.contains(node)) {
                    roots.add(node);
                }
                continue;
            }

            if (!parent.getChildren().contains(node)) {
                parent.addChild(node);
            }
        }

        return roots;
    }

    public List<OptionItemDto> getRegions() {
        return REGION_OPTIONS;
    }

    public List<OptionItemDto> getEmploymentTypes() {
        return EMPLOYMENT_TYPE_OPTIONS;
    }

    public List<OptionItemDto> getSalaryTypes() {
        return SALARY_TYPE_OPTIONS;
    }

    private JobCategoryTreeNodeDto findParentNode(Map<String, JobCategoryTreeNodeDto> nodeByCode, JobCategoryRaw row) {
        if (row.level() == null || row.level() <= 1) {
            return null;
        }

        String normalizedCode = normalizeCode(row.code());

        return nodeByCode.values().stream()
                .filter(candidate -> candidate.getLevel() != null)
                .filter(candidate -> candidate.getLevel() == row.level() - 1)
                .filter(candidate -> normalizedCode.startsWith(normalizeCode(candidate.getCode())))
                .max(Comparator.comparingInt(candidate -> normalizeCode(candidate.getCode()).length()))
                .orElse(null);
    }

    private Integer parseLevel(String levelText) {
        if (levelText == null) {
            return null;
        }

        String trimmed = levelText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private record JobCategoryRaw(String code, String name, Integer level) {
    }
}
