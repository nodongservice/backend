package com.bridgework.options.service;

import com.bridgework.options.dto.JobCategoryTreeNodeDto;
import com.bridgework.options.dto.OptionItemDto;
import com.bridgework.options.enums.FilterSalaryType;
import com.bridgework.profile.enums.ProfileResidenceRegion;
import com.bridgework.profile.enums.ProfileWorkType;
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

    private static final List<OptionItemDto> REGION_OPTIONS = List.of(ProfileResidenceRegion.values()).stream()
            .map(value -> new OptionItemDto(value.name(), value.getKoreanLabel()))
            .toList();

    private static final List<OptionItemDto> EMPLOYMENT_TYPE_OPTIONS = List.of(ProfileWorkType.values()).stream()
            .map(value -> new OptionItemDto(value.name(), value.getKoreanLabel()))
            .toList();

    private static final List<OptionItemDto> SALARY_TYPE_OPTIONS = List.of(FilterSalaryType.values()).stream()
            .map(value -> new OptionItemDto(value.name(), value.getKoreanLabel()))
            .toList();

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
