package com.bridgework.sync.normalized;

import com.bridgework.sync.exception.ExternalApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NaverGeocodingService {

    private static final String GEOCODE_ENDPOINT = "https://maps.apigw.ntruss.com/map-geocode/v2/geocode";

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

        String requestUri = UriComponentsBuilder.fromHttpUrl(GEOCODE_ENDPOINT)
                .queryParam("query", queryAddress)
                .build(true)
                .toUriString();

        String responseBody = webClient
                .get()
                .uri(requestUri)
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
}
