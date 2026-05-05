package com.bridgework.sync.normalized;

import com.bridgework.sync.exception.ExternalApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NaverGeocodingService {

    private static final Logger log = LoggerFactory.getLogger(NaverGeocodingService.class);
    private static final String GEOCODE_ENDPOINT = "https://maps.apigw.ntruss.com/map-geocode/v2/geocode";
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern PAREN_CONTENT_PATTERN = Pattern.compile("\\([^)]*\\)");
    private static final Pattern PAREN_LOCALITY_PATTERN = Pattern.compile("\\(([^)]*(?:동|읍|면|리)[^)]*)\\)");
    private static final Pattern LEADING_POSTAL_CODE_PATTERN = Pattern.compile("^\\(?\\d{5}\\)?\\s*");
    private static final Pattern TRAILING_UNIT_PATTERN = Pattern.compile(
            "(\\s+\\d{1,4}(?:[-~]\\d{1,4})?\\s*호\\s*)$"
                    + "|(\\s*\\d{1,3}\\s*층\\s*)$"
                    + "|(\\s*B\\d{1,2}\\s*층\\s*)$"
                    + "|(\\s*지하\\s*\\d{1,2}\\s*층\\s*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAILING_FLOOR_ROOM_PATTERN = Pattern.compile(
            "(\\s+\\d{1,4}(?:[-~]\\d{1,4})?)$"
    );
    private static final Pattern ROAD_NUMBER_END_PATTERN = Pattern.compile("^(.+?\\d+(?:-\\d+)?)\\b.*$");
    private static final Pattern ROAD_NAME_ONLY_PATTERN = Pattern.compile("^(.+?(?:로|길|대로))\\s+\\d+(?:-\\d+)?$");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public NaverGeocodingService(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public Optional<NormalizedGeoPoint> geocode(String apiKeyId, String apiKey, String queryAddress) {
        if (apiKeyId == null || apiKeyId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        if (queryAddress == null || queryAddress.isBlank()) {
            return Optional.empty();
        }

        List<String> queryCandidates = buildQueryCandidates(queryAddress);
        for (String queryCandidate : queryCandidates) {
            Optional<NormalizedGeoPoint> point = requestGeocode(apiKeyId, apiKey, queryCandidate);
            if (point.isPresent()) {
                return point;
            }
        }
        log.warn("지오코딩 매칭 실패 originalAddress={} candidates={}", queryAddress, queryCandidates);
        return Optional.empty();
    }

    private Optional<NormalizedGeoPoint> requestGeocode(String apiKeyId, String apiKey, String queryAddress) {
        String requestUri = UriComponentsBuilder.fromHttpUrl(GEOCODE_ENDPOINT)
                .queryParam("query", queryAddress)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        String responseBody = webClient
                .get()
                .uri(URI.create(requestUri))
                .header("x-ncp-apigw-api-key-id", apiKeyId)
                .header("x-ncp-apigw-api-key", apiKey)
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .blockOptional()
                .orElseThrow(() -> new ExternalApiException("네이버 지오코딩 응답이 비어 있습니다."));

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode addresses = root.path("addresses");
            if (!addresses.isArray() || addresses.isEmpty()) {
                return Optional.empty();
            }

            JsonNode first = addresses.get(0);
            String latitudeText = first.path("y").asText("").trim();
            String longitudeText = first.path("x").asText("").trim();
            if (latitudeText.isBlank() || longitudeText.isBlank()) {
                return Optional.empty();
            }

            Double latitude = Double.valueOf(latitudeText);
            Double longitude = Double.valueOf(longitudeText);
            String roadAddress = first.path("roadAddress").asText("").trim();
            String jibunAddress = first.path("jibunAddress").asText("").trim();
            String matchedAddress = roadAddress.isBlank() ? jibunAddress : roadAddress;
            return Optional.of(new NormalizedGeoPoint(latitude, longitude, matchedAddress));
        } catch (Exception exception) {
            throw new ExternalApiException("네이버 지오코딩 파싱 실패: " + exception.getMessage());
        }
    }

    private List<String> buildQueryCandidates(String queryAddress) {
        Set<String> candidates = new LinkedHashSet<>();
        String normalized = normalizeSpaces(queryAddress);
        if (!normalized.isBlank()) {
            candidates.add(normalized);
        }

        String withoutPostalCode = LEADING_POSTAL_CODE_PATTERN.matcher(normalized).replaceFirst("").trim();
        if (!withoutPostalCode.isBlank()) {
            candidates.add(withoutPostalCode);
        }

        String withoutParen = normalizeSpaces(PAREN_CONTENT_PATTERN.matcher(withoutPostalCode).replaceAll(" "));
        if (!withoutParen.isBlank()) {
            candidates.add(withoutParen);
        }

        String withoutTrailingUnit = normalizeSpaces(TRAILING_UNIT_PATTERN.matcher(withoutParen).replaceFirst(""));
        if (!withoutTrailingUnit.isBlank()) {
            candidates.add(withoutTrailingUnit);
        }

        String roomFloorDropped = normalizeSpaces(TRAILING_FLOOR_ROOM_PATTERN.matcher(withoutTrailingUnit).replaceFirst(""));
        if (!roomFloorDropped.isBlank()) {
            candidates.add(roomFloorDropped);
        }

        String numberAnchored = extractRoadNumberPrefix(roomFloorDropped);
        if (!numberAnchored.isBlank()) {
            candidates.add(numberAnchored);
        }

        String roadNameOnly = extractRoadNameOnly(numberAnchored);
        if (!roadNameOnly.isBlank()) {
            candidates.add(roadNameOnly);
        }

        String districtLocality = buildDistrictLocalityCandidate(withoutPostalCode, withoutParen);
        if (!districtLocality.isBlank()) {
            candidates.add(districtLocality);
        }

        return new ArrayList<>(candidates);
    }

    private String extractRoadNumberPrefix(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        var matcher = ROAD_NUMBER_END_PATTERN.matcher(address);
        if (!matcher.matches()) {
            return "";
        }
        return normalizeSpaces(matcher.group(1));
    }

    private String extractRoadNameOnly(String roadNumberAddress) {
        if (roadNumberAddress == null || roadNumberAddress.isBlank()) {
            return "";
        }
        var matcher = ROAD_NAME_ONLY_PATTERN.matcher(roadNumberAddress);
        if (!matcher.matches()) {
            return "";
        }
        return normalizeSpaces(matcher.group(1));
    }

    private String buildDistrictLocalityCandidate(String addressWithParen, String addressWithoutParen) {
        if (addressWithParen == null || addressWithParen.isBlank()
                || addressWithoutParen == null || addressWithoutParen.isBlank()) {
            return "";
        }

        String localityToken = extractParenLocality(addressWithParen);
        if (localityToken.isBlank()) {
            return "";
        }

        String districtPrefix = extractDistrictPrefix(addressWithoutParen);
        if (districtPrefix.isBlank()) {
            return "";
        }

        return normalizeSpaces(districtPrefix + " " + localityToken);
    }

    private String extractParenLocality(String address) {
        var matcher = PAREN_LOCALITY_PATTERN.matcher(address);
        if (!matcher.find()) {
            return "";
        }
        return normalizeSpaces(matcher.group(1));
    }

    private String extractDistrictPrefix(String addressWithoutParen) {
        String normalized = normalizeSpaces(addressWithoutParen);
        if (normalized.isBlank()) {
            return "";
        }

        String[] tokens = normalized.split(" ");
        int lastDistrictTokenIndex = -1;
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            if (token.endsWith("시") || token.endsWith("군") || token.endsWith("구")) {
                lastDistrictTokenIndex = index;
            }
        }

        if (lastDistrictTokenIndex < 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index <= lastDistrictTokenIndex; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(tokens[index]);
        }
        return builder.toString();
    }

    private String normalizeSpaces(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return MULTI_SPACE_PATTERN.matcher(value).replaceAll(" ").trim();
    }
}
