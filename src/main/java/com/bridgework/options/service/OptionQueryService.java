package com.bridgework.options.service;

import com.bridgework.options.dto.JobCategoryTreeNodeDto;
import com.bridgework.options.dto.OptionItemDto;
import com.bridgework.options.enums.FilterSalaryType;
import com.bridgework.profile.enums.ProfileResidenceRegion;
import com.bridgework.profile.enums.ProfileWorkType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(cacheNames = "jobCategoryTree", sync = true)
    public List<JobCategoryTreeNodeDto> getJobCategoryTree() {
        String sql = """
                SELECT DISTINCT job_cd, job_cd_nm, job_cd_level
                FROM pd_kepad_job_category
                WHERE job_cd IS NOT NULL
                  AND job_cd_nm IS NOT NULL
                """;

        List<JobCategoryRaw> fetchedRows = namedParameterJdbcTemplate.query(
                sql,
                (rs, rowNum) -> new JobCategoryRaw(
                        rs.getString("job_cd"),
                        rs.getString("job_cd_nm"),
                        parseLevel(rs.getString("job_cd_level")),
                        normalizeCode(rs.getString("job_cd"))
                )
        );

        List<JobCategoryRaw> rows = fetchedRows.stream()
                .sorted(Comparator
                        .comparing((JobCategoryRaw raw) -> raw.level() == null ? Integer.MAX_VALUE : raw.level())
                        .thenComparing(JobCategoryRaw::code))
                .toList();

        Map<String, JobCategoryRaw> uniqueRowsByCode = new LinkedHashMap<>();
        for (JobCategoryRaw row : rows) {
            uniqueRowsByCode.putIfAbsent(row.code(), row);
        }
        List<JobCategoryRaw> uniqueRows = List.copyOf(uniqueRowsByCode.values());

        Map<String, JobCategoryTreeNodeDto> nodeByCode = new LinkedHashMap<>();
        Map<Integer, Map<String, JobCategoryTreeNodeDto>> nodesByLevelAndNormalizedCode = new HashMap<>();
        List<JobCategoryTreeNodeDto> roots = new ArrayList<>();

        for (JobCategoryRaw row : uniqueRows) {
            JobCategoryTreeNodeDto node = new JobCategoryTreeNodeDto(row.code(), row.name(), row.level());
            nodeByCode.put(row.code(), node);
            if (row.level() != null && !row.normalizedCode().isEmpty()) {
                nodesByLevelAndNormalizedCode
                        .computeIfAbsent(row.level(), key -> new HashMap<>())
                        .put(row.normalizedCode(), node);
            }
        }

        for (JobCategoryRaw row : uniqueRows) {
            JobCategoryTreeNodeDto node = nodeByCode.get(row.code());
            JobCategoryTreeNodeDto parent = findParentNode(nodesByLevelAndNormalizedCode, row);
            if (parent == null) {
                roots.add(node);
                continue;
            }
            parent.addChild(node);
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

    private JobCategoryTreeNodeDto findParentNode(Map<Integer, Map<String, JobCategoryTreeNodeDto>> nodesByLevelAndNormalizedCode,
                                                  JobCategoryRaw row) {
        if (row.level() == null || row.level() <= 1) {
            return null;
        }

        Map<String, JobCategoryTreeNodeDto> parentCandidates = nodesByLevelAndNormalizedCode.get(row.level() - 1);
        if (parentCandidates == null || parentCandidates.isEmpty()) {
            return null;
        }

        String normalizedCode = row.normalizedCode();
        for (int prefixLength = normalizedCode.length() - 1; prefixLength > 0; prefixLength--) {
            JobCategoryTreeNodeDto parent = parentCandidates.get(normalizedCode.substring(0, prefixLength));
            if (parent != null) {
                return parent;
            }
        }
        return null;
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

    private record JobCategoryRaw(String code, String name, Integer level, String normalizedCode) {
    }
}
