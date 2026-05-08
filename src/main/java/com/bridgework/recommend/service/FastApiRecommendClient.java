package com.bridgework.recommend.service;

import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.recommend.config.BridgeWorkRecommendProperties;
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
            return response;
        } catch (WebClientResponseException exception) {
            throw new RecommendDomainException(
                    "FASTAPI_HTTP_ERROR",
                    HttpStatus.BAD_GATEWAY,
                    "FastAPI 호출 실패: status=" + exception.getStatusCode().value(),
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

    private Map<String, Object> buildScoreProfile(UserProfileResponseDto profile) {
        Map<String, Object> scoreProfile = new LinkedHashMap<>();
        scoreProfile.put("profile_id", profile.profileId());
        scoreProfile.put("user_id", profile.userId());
        scoreProfile.put("name", profile.fullName());
        scoreProfile.put("address", joinAddress(profile.residenceRegion(), profile.detailAddress()));
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

    private String joinAddress(String region, String detailAddress) {
        if ((region == null || region.isBlank()) && (detailAddress == null || detailAddress.isBlank())) {
            return null;
        }
        if (region == null || region.isBlank()) {
            return detailAddress;
        }
        if (detailAddress == null || detailAddress.isBlank()) {
            return region;
        }
        return region + " " + detailAddress;
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
}

