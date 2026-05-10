package com.bridgework.recommend.service;

import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.recommend.config.BridgeWorkRecommendProperties;
import com.bridgework.recommend.dto.RecommendExplainEvidenceItemDto;
import com.bridgework.recommend.dto.RecommendExplainJobDto;
import com.bridgework.recommend.dto.RecommendExplainProfileDto;
import com.bridgework.recommend.dto.RecommendExplainRequestDto;
import com.bridgework.recommend.dto.RecommendExplainSelectedResultDto;
import com.bridgework.recommend.dto.RecommendExplainScoreDetailDto;
import com.bridgework.recommend.exception.RecommendDomainException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FastApiRecommendClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("(\\d+)");

    private final WebClient webClient;
    private final BridgeWorkRecommendProperties recommendProperties;

    public FastApiRecommendClient(WebClient webClient, BridgeWorkRecommendProperties recommendProperties) {
        this.webClient = webClient;
        this.recommendProperties = recommendProperties;
    }

    public Map<String, Object> requestQuickScore(UserProfileResponseDto profile) {
        return post(recommendProperties.getQuickPath(), buildProfilePayload(profile));
    }

    public Map<String, Object> requestMapScore(UserProfileResponseDto profile) {
        return post(recommendProperties.getMapPath(), buildProfilePayload(profile));
    }

    public Map<String, Object> requestRecommendationExplain(
            RecommendExplainRequestDto request
    ) {
        return post(recommendProperties.getExplainPath(), buildExplainPayload(request));
    }

    private Map<String, Object> post(String path, Map<String, Object> payload) {
        String uri = UriComponentsBuilder.fromUriString(recommendProperties.getFastapiBaseUrl())
                .path(normalizePath(path))
                .build(true)
                .toUriString();

        try {
            Map<String, Object> response = webClient.post()
                    .uri(uri)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(recommendProperties.getRequestTimeout())
                    .block();

            if (response == null) {
                throw new RecommendDomainException(
                        "FASTAPI_EMPTY_RESPONSE",
                        HttpStatus.BAD_GATEWAY,
                        "FastAPI 응답이 비어 있습니다."
                );
            }
            if (!isSupportedResponseShape(response)) {
                throw new RecommendDomainException(
                        "FASTAPI_INVALID_RESPONSE",
                        HttpStatus.BAD_GATEWAY,
                        "FastAPI 응답 형식이 예상과 다릅니다. (code/message/result 또는 results 필요)"
                );
            }
            return response;
        } catch (WebClientResponseException exception) {
            String responseBody = sanitizeErrorBody(exception.getResponseBodyAsString());
            throw new RecommendDomainException(
                    "FASTAPI_HTTP_ERROR",
                    HttpStatus.BAD_GATEWAY,
                    "FastAPI 호출 실패: status=" + exception.getStatusCode().value()
                            + (responseBody == null ? "" : ", body=" + responseBody),
                    exception
            );
        } catch (RecommendDomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RecommendDomainException(
                    "FASTAPI_CALL_FAILED",
                    HttpStatus.BAD_GATEWAY,
                    "FastAPI 호출 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private Map<String, Object> buildProfilePayload(UserProfileResponseDto profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile", buildScoreProfile(profile));
        return payload;
    }

    private Map<String, Object> buildExplainPayload(RecommendExplainRequestDto request) {
        RecommendExplainSelectedResultDto selectedResult = request.selectedResult();
        RecommendExplainJobDto job = request.job() != null
                ? request.job()
                : (selectedResult == null ? null : selectedResult.job());
        if (job == null) {
            throw new RecommendDomainException(
                    "EXPLAIN_JOB_REQUIRED",
                    HttpStatus.BAD_REQUEST,
                    "job 또는 selectedResult.job이 필요합니다."
            );
        }

        RecommendExplainScoreDetailDto scoreDetail = request.scoreDetail() != null
                ? request.scoreDetail()
                : (selectedResult == null ? null : selectedResult.scoreDetail());
        Integer totalScore = request.totalScore() != null
                ? request.totalScore()
                : (selectedResult == null ? null : selectedResult.totalScore());
        Integer jobFitScore = request.jobFitScore() != null
                ? request.jobFitScore()
                : (selectedResult == null ? null : selectedResult.jobFitScore());
        List<String> reasons = request.reasons() != null
                ? request.reasons()
                : (selectedResult == null ? null : selectedResult.reasons());
        List<String> riskFactors = request.riskFactors() != null
                ? request.riskFactors()
                : (selectedResult == null ? null : selectedResult.riskFactors());
        List<RecommendExplainEvidenceItemDto> evidenceItems = request.evidenceItems() != null
                ? request.evidenceItems()
                : (selectedResult == null ? null : selectedResult.evidenceItems());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile", toExplainProfilePayload(request.profile()));
        payload.put("job", toExplainJobPayload(job));
        payload.put("score_detail", toExplainScoreDetailPayload(scoreDetail));
        payload.put("total_score", totalScore);
        payload.put("job_fit_score", jobFitScore);
        payload.put("reasons", nullToEmpty(reasons));
        payload.put("risk_factors", nullToEmpty(riskFactors));
        payload.put("evidence_items", toExplainEvidencePayload(evidenceItems));
        return payload;
    }

    private Map<String, Object> toExplainProfilePayload(RecommendExplainProfileDto profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile_id", profile.profileId());
        payload.put("user_id", profile.userId());
        payload.put("name", profile.name());
        payload.put("address", profile.address());
        payload.put("home_lat", profile.homeLat());
        payload.put("home_lng", profile.homeLng());
        payload.put("desired_jobs", nullToEmpty(profile.desiredJobs()));
        payload.put("skills", nullToEmpty(profile.skills()));
        payload.put("education", profile.education());
        payload.put("career", profile.career());
        payload.put("major", profile.major());
        payload.put("licenses", nullToEmpty(profile.licenses()));
        payload.put("job_fit_statement", profile.jobFitStatement());
        payload.put("available_employment_types", nullToEmpty(profile.availableEmploymentTypes()));
        payload.put("desired_salary", profile.desiredSalary());
        payload.put("time_preference", profile.timePreference());
        payload.put("remote_work", profile.remoteWork());
        payload.put("disability_types", nullToEmpty(profile.disabilityTypes()));
        payload.put("disability_severity", profile.disabilitySeverity());
        payload.put("is_registered_disabled", profile.isRegisteredDisabled());
        payload.put("disability_description", profile.disabilityDescription());
        payload.put("assistive_devices", nullToEmpty(profile.assistiveDevices()));
        payload.put("required_supports", nullToEmpty(profile.requiredSupports()));
        payload.put("mobility_range_km", profile.mobilityRangeKm());
        return payload;
    }

    private Map<String, Object> toExplainJobPayload(RecommendExplainJobDto job) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job_post_id", resolveJobPostId(job));
        payload.put("company_name", job.companyName());
        payload.put("job_title", job.jobTitle());
        payload.put("work_address", job.workAddress());
        payload.put("work_lat", job.workLat());
        payload.put("work_lng", job.workLng());
        payload.put("employment_type", job.employmentType());
        payload.put("enter_type", job.enterType());
        payload.put("salary_type", job.salaryType());
        payload.put("salary", job.salary());
        payload.put("term_date", job.termDate());
        payload.put("required_career", job.requiredCareer());
        payload.put("required_education", job.requiredEducation());
        payload.put("required_major", job.requiredMajor());
        payload.put("required_licenses", job.requiredLicenses());
        payload.put("environment", Map.of());
        payload.put("agency_name", job.agencyName());
        payload.put("registered_at", job.registeredAt());
        payload.put("source_table", job.sourceTable());
        payload.put("source_id", job.sourceId());
        return payload;
    }

    private Long resolveJobPostId(RecommendExplainJobDto job) {
        if (job.jobPostId() != null) {
            return job.jobPostId();
        }
        if (job.sourceId() != null) {
            return job.sourceId();
        }
        throw new RecommendDomainException(
                "JOB_POST_ID_REQUIRED",
                HttpStatus.BAD_REQUEST,
                "jobPostId 또는 sourceId가 필요합니다."
        );
    }

    private Map<String, Object> toExplainScoreDetailPayload(RecommendExplainScoreDetailDto detail) {
        if (detail == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job_fit_score", detail.jobFitScore());
        payload.put("work_condition_score", detail.workConditionScore());
        payload.put("disability_support_score", detail.disabilitySupportScore());
        payload.put("work_environment_score", detail.workEnvironmentScore());
        payload.put("company_stability_score", detail.companyStabilityScore());
        payload.put("accessibility_score", detail.accessibilityScore());
        return payload;
    }

    private List<Map<String, Object>> toExplainEvidencePayload(List<RecommendExplainEvidenceItemDto> evidenceItems) {
        if (evidenceItems == null || evidenceItems.isEmpty()) {
            return List.of();
        }
        return evidenceItems.stream()
                .map(this::toExplainEvidenceItem)
                .toList();
    }

    private Map<String, Object> toExplainEvidenceItem(RecommendExplainEvidenceItemDto item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_type", item.sourceType());
        payload.put("source_name", item.sourceName());
        payload.put("description", item.description());
        payload.put("distance_meters", item.distanceMeters());
        payload.put("source_table", item.sourceTable());
        payload.put("record_id", item.recordId());
        payload.put("fields", item.fields() == null ? Map.of() : item.fields());
        return payload;
    }

    private Map<String, Object> buildScoreProfile(UserProfileResponseDto profile) {
        Map<String, Object> scoreProfile = new LinkedHashMap<>();
        scoreProfile.put("profile_id", profile.profileId());
        scoreProfile.put("user_id", profile.userId());
        scoreProfile.put("name", profile.fullName());
        scoreProfile.put("address", profile.detailAddress());
        scoreProfile.put("desired_jobs", desiredJobs(profile));
        scoreProfile.put("skills", nullToEmpty(profile.skills()));
        scoreProfile.put("education", firstNotBlank(profile.highestEducation(), profile.educationSummary()));
        scoreProfile.put("career", firstNotBlank(profile.careerSummary(), profile.majorCareer()));
        scoreProfile.put("major", profile.majorCareer());
        scoreProfile.put("licenses", nullToEmpty(profile.certifications()));
        scoreProfile.put("job_fit_statement", profile.jobFitDescription());
        scoreProfile.put("available_employment_types", nullToEmpty(profile.workTypes()));
        scoreProfile.put("desired_salary", extractInteger(profile.expectedSalary()));
        scoreProfile.put("time_preference", profile.workTimePreference());
        scoreProfile.put("remote_work", profile.remoteAvailableYn());
        scoreProfile.put("disability_types", singleOrEmpty(profile.disabilityType()));
        scoreProfile.put("disability_severity", profile.disabilitySeverity());
        scoreProfile.put("is_registered_disabled", profile.disabilityRegisteredYn());
        scoreProfile.put("disability_description", profile.disabilityDescription());
        scoreProfile.put("assistive_devices", splitToList(profile.assistiveDevices()));
        scoreProfile.put("required_supports", mergeRequiredSupports(profile.requiredSupports(), profile.workSupportRequirements()));
        scoreProfile.put("mobility_range_km", extractDecimal(profile.mobilityRange()));
        return scoreProfile;
    }

    private List<String> desiredJobs(UserProfileResponseDto profile) {
        Set<String> merged = new LinkedHashSet<>();
        addIfNotBlank(merged, profile.targetJob());
        addIfNotBlank(merged, profile.desiredJob());
        merged.addAll(nullToEmpty(profile.aiJobTags()));
        return new ArrayList<>(merged);
    }

    private List<String> mergeRequiredSupports(List<String> requiredSupports, String workSupportRequirements) {
        Set<String> merged = new LinkedHashSet<>(nullToEmpty(requiredSupports));
        merged.addAll(splitToList(workSupportRequirements));
        return new ArrayList<>(merged);
    }

    private void addIfNotBlank(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<String> singleOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value);
    }

    private List<String> splitToList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,/]"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private Integer extractInteger(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = INTEGER_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double extractDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = DECIMAL_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String firstNotBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String sanitizeErrorBody(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.replaceAll("\\s+", " ").trim();
        if (compact.isEmpty()) {
            return null;
        }
        int maxLength = 300;
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }

    private boolean isSupportedResponseShape(Map<String, Object> response) {
        boolean hasWrapped = response.containsKey("code") && response.containsKey("message") && response.containsKey("result");
        boolean hasLegacy = response.containsKey("results");
        return hasWrapped || hasLegacy;
    }
}
