package com.bridgework.recommend.service;

import com.bridgework.common.config.BridgeWorkHealthMonitorProperties;
import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.recommend.config.BridgeWorkRecommendProperties;
import com.bridgework.recommend.dto.RecommendExplainEvidenceItemDto;
import com.bridgework.recommend.dto.RecommendExplainJobDto;
import com.bridgework.recommend.dto.RecommendExplainProfileDto;
import com.bridgework.recommend.dto.RecommendExplainRequestDto;
import com.bridgework.recommend.dto.RecommendExplainSelectedResultDto;
import com.bridgework.recommend.dto.RecommendExplainScoreDetailDto;
import com.bridgework.recommend.exception.RecommendDomainException;
import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.normalized.NaverGeocodingService;
import com.bridgework.sync.normalized.NormalizedGeoPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FastApiRecommendClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };
    private static final Pattern INTEGER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern HOUR_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*시간");
    private static final Pattern MINUTE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*분");
    private static final List<String> RETRYABLE_MESSAGE_KEYWORDS = List.of(
            "connection prematurely closed before response",
            "connection reset by peer",
            "broken pipe"
    );

    private final WebClient webClient;
    private final BridgeWorkRecommendProperties recommendProperties;
    private final BridgeWorkHealthMonitorProperties healthMonitorProperties;
    private final BridgeWorkSyncProperties syncProperties;
    private final NaverGeocodingService naverGeocodingService;
    private final Map<String, Optional<NormalizedGeoPoint>> homeGeoCache = new ConcurrentHashMap<>();

    public FastApiRecommendClient(
            WebClient webClient,
            BridgeWorkRecommendProperties recommendProperties,
            BridgeWorkHealthMonitorProperties healthMonitorProperties,
            BridgeWorkSyncProperties syncProperties,
            NaverGeocodingService naverGeocodingService
    ) {
        this.webClient = webClient;
        this.recommendProperties = recommendProperties;
        this.healthMonitorProperties = healthMonitorProperties;
        this.syncProperties = syncProperties;
        this.naverGeocodingService = naverGeocodingService;
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
        List<String> candidateUris = new ArrayList<>(resolveCandidateUris(path));
        LinkedHashSet<String> discoveredRedirectUris = new LinkedHashSet<>();
        List<String> attemptFailures = new ArrayList<>();
        int maxAttemptsPerUri = 2;

        for (int index = 0; index < candidateUris.size(); index++) {
            String uri = candidateUris.get(index);
            boolean hasNextUri = index < candidateUris.size() - 1;

            for (int attempt = 1; attempt <= maxAttemptsPerUri; attempt++) {
                boolean hasNextAttempt = attempt < maxAttemptsPerUri;

                try {
                    return executePost(uri, payload);
                } catch (WebClientResponseException exception) {
                    String redirectLocation = exception.getHeaders().getFirst("Location");
                    String responseBody = sanitizeErrorBody(exception.getResponseBodyAsString());
                    String failureMessage = "status=" + exception.getStatusCode().value()
                            + ", uri=" + uri
                            + ", attempt=" + attempt
                            + (StringUtils.hasText(redirectLocation) ? ", location=" + redirectLocation : "")
                            + (responseBody == null ? "" : ", body=" + responseBody);
                    attemptFailures.add(failureMessage);

                    boolean retryableHttpStatus = isRetryableHttpStatus(exception.getStatusCode());
                    if (retryableHttpStatus && StringUtils.hasText(redirectLocation)) {
                        String redirectUri = normalizeAbsoluteUri(redirectLocation);
                        if (StringUtils.hasText(redirectUri) && discoveredRedirectUris.add(redirectUri)) {
                            candidateUris.add(index + 1, redirectUri);
                            hasNextUri = true;
                        }
                    }
                    if (retryableHttpStatus && hasNextAttempt) {
                        continue;
                    }
                    if (retryableHttpStatus && hasNextUri) {
                        break;
                    }

                    throw new RecommendDomainException(
                            "FASTAPI_HTTP_ERROR",
                            HttpStatus.BAD_GATEWAY,
                            "FastAPI 호출 실패: " + failureMessage,
                            exception
                    );
                } catch (RecommendDomainException exception) {
                    throw exception;
                } catch (Exception exception) {
                    String failureMessage = "uri=" + uri
                            + ", attempt=" + attempt
                            + ", reason=" + summarizeException(exception);
                    attemptFailures.add(failureMessage);

                    boolean retryableConnectionException = isRetryableConnectionException(exception);
                    if (retryableConnectionException && hasNextAttempt) {
                        continue;
                    }
                    if (retryableConnectionException && hasNextUri) {
                        break;
                    }

                    throw new RecommendDomainException(
                            "FASTAPI_CALL_FAILED",
                            HttpStatus.BAD_GATEWAY,
                            "FastAPI 호출 중 오류가 발생했습니다: " + summarizeException(exception),
                            exception
                    );
                }
            }
        }

        throw new RecommendDomainException(
                "FASTAPI_CALL_FAILED",
                HttpStatus.BAD_GATEWAY,
                "FastAPI 호출 실패: " + String.join(" | ", attemptFailures)
        );
    }

    private Map<String, Object> executePost(String uri, Map<String, Object> payload) {
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
    }

    private List<String> resolveCandidateUris(String path) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        // 운영에서는 blue/green 슬롯 헬스 경로를 기준으로 직접 호출 경로를 우선 구성한다.
        for (String healthUrl : healthMonitorProperties.getFastapiHealthUrls()) {
            String baseUrl = toBaseUrlFromHealthUrl(healthUrl);
            if (!StringUtils.hasText(baseUrl)) {
                continue;
            }
            candidates.add(joinBaseUrlAndPath(baseUrl, path));
        }

        candidates.add(joinBaseUrlAndPath(recommendProperties.getFastapiBaseUrl(), path));
        return List.copyOf(candidates);
    }

    private String toBaseUrlFromHealthUrl(String healthUrl) {
        if (!StringUtils.hasText(healthUrl)) {
            return "";
        }
        String normalized = StringUtils.trimWhitespace(healthUrl);
        int healthPathIndex = normalized.indexOf("/health");
        if (healthPathIndex < 0) {
            return normalized;
        }
        return normalized.substring(0, healthPathIndex);
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
        payload.put("distance_score", detail.distanceScore());
        payload.put("commute_score", detail.commuteScore());
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
        NormalizedGeoPoint homePoint = resolveHomeGeoPoint(profile).orElse(null);
        Map<String, Object> scoreProfile = new LinkedHashMap<>();
        scoreProfile.put("profile_id", profile.profileId());
        scoreProfile.put("user_id", profile.userId());
        scoreProfile.put("name", profile.fullName());
        scoreProfile.put("address", profile.detailAddress());
        scoreProfile.put("home_lat", homePoint == null ? null : homePoint.latitude());
        scoreProfile.put("home_lng", homePoint == null ? null : homePoint.longitude());
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
        scoreProfile.put("mobility_range_km", parseMobilityRangeKm(profile.commuteRange()));
        scoreProfile.put("commute_limit_minutes", parseCommuteLimitMinutes(profile.commuteRange()));
        return scoreProfile;
    }

    private Optional<NormalizedGeoPoint> resolveHomeGeoPoint(UserProfileResponseDto profile) {
        if (profile.homeLat() != null && profile.homeLng() != null) {
            return Optional.of(new NormalizedGeoPoint(
                    profile.homeLat(),
                    profile.homeLng(),
                    profile.homeGeocodedAddress()
            ));
        }
        return resolveHomeGeoPoint(profile.detailAddress());
    }

    private Optional<NormalizedGeoPoint> resolveHomeGeoPoint(String address) {
        if (!StringUtils.hasText(address)) {
            return Optional.empty();
        }
        String normalizedAddress = address.trim();
        return homeGeoCache.computeIfAbsent(normalizedAddress, value -> {
            try {
                return naverGeocodingService.geocode(
                        syncProperties.getNaverGeocodeApiKeyId(),
                        syncProperties.getNaverGeocodeApiKey(),
                        value
                );
            } catch (Exception ignored) {
                return Optional.empty();
            }
        });
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

    private Double parseMobilityRangeKm(String commuteRange) {
        if (!StringUtils.hasText(commuteRange)) {
            return null;
        }

        String normalized = commuteRange.trim().toLowerCase();
        Matcher matcher = DECIMAL_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }

        double value = Double.parseDouble(matcher.group(1));
        if (normalized.contains("km") || normalized.contains("킬로")) {
            return value;
        }
        if (normalized.contains("m") || normalized.contains("미터")) {
            return value / 1000.0;
        }
        return null;
    }

    private Integer parseCommuteLimitMinutes(String commuteRange) {
        if (!StringUtils.hasText(commuteRange)) {
            return null;
        }

        String normalized = commuteRange.trim().toLowerCase();
        Matcher hourMatcher = HOUR_PATTERN.matcher(normalized);
        Matcher minuteMatcher = MINUTE_PATTERN.matcher(normalized);
        double totalMinutes = 0;
        if (hourMatcher.find()) {
            totalMinutes += Double.parseDouble(hourMatcher.group(1)) * 60;
        }
        if (minuteMatcher.find()) {
            totalMinutes += Double.parseDouble(minuteMatcher.group(1));
        }
        if (totalMinutes > 0) {
            return (int) Math.round(totalMinutes);
        }

        Matcher matcher = DECIMAL_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }

        double value = Double.parseDouble(matcher.group(1));
        if (normalized.contains("시간")) {
            return (int) Math.round(value * 60);
        }
        if (normalized.contains("분") || normalized.contains("minute") || normalized.contains("min")) {
            return (int) Math.round(value);
        }
        return null;
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
        if (!StringUtils.hasText(path)) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String joinBaseUrlAndPath(String baseUrl, String path) {
        String normalizedBase = StringUtils.trimWhitespace(baseUrl);
        if (!StringUtils.hasText(normalizedBase)) {
            return normalizePath(path);
        }
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        return normalizedBase + normalizePath(path);
    }

    private String normalizeAbsoluteUri(String uri) {
        if (!StringUtils.hasText(uri)) {
            return "";
        }
        String normalized = StringUtils.trimWhitespace(uri);
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return "";
        }
        return normalized;
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

    private boolean isRetryableHttpStatus(HttpStatusCode statusCode) {
        int status = statusCode.value();
        return (status >= 300 && status < 400) || status >= 500;
    }

    private boolean isRetryableConnectionException(Exception exception) {
        if (exception instanceof WebClientRequestException || hasCauseOfType(exception, TimeoutException.class)) {
            return true;
        }
        for (String keyword : RETRYABLE_MESSAGE_KEYWORDS) {
            if (containsKeywordInCauseChain(exception, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCauseOfType(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsKeywordInCauseChain(Throwable throwable, String keyword) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (StringUtils.hasText(message) && message.toLowerCase().contains(keyword)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String summarizeException(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getMessage();
        }
        if (!StringUtils.hasText(message)) {
            return rootCause.getClass().getSimpleName();
        }
        return rootCause.getClass().getSimpleName() + ": " + message;
    }
}
