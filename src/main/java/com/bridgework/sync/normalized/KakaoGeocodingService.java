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
public class KakaoGeocodingService {

    private static final String GEOCODE_ENDPOINT = "https://dapi.kakao.com/v2/local/search/address.json";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public KakaoGeocodingService(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public Optional<NormalizedGeoPoint> geocode(String restApiKey, String queryAddress) {
        if (restApiKey == null || restApiKey.isBlank()) {
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
                .header("Authorization", "KakaoAK " + restApiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .blockOptional()
                .orElseThrow(() -> new ExternalApiException("카카오 지오코딩 응답이 비어 있습니다."));

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode documents = root.path("documents");
            if (!documents.isArray() || documents.isEmpty()) {
                return Optional.empty();
            }

            JsonNode first = documents.get(0);
            String latitudeText = first.path("y").asText("").trim();
            String longitudeText = first.path("x").asText("").trim();
            if (latitudeText.isBlank() || longitudeText.isBlank()) {
                return Optional.empty();
            }

            Double latitude = Double.valueOf(latitudeText);
            Double longitude = Double.valueOf(longitudeText);
            String matchedAddress = first.path("address_name").asText("").trim();
            return Optional.of(new NormalizedGeoPoint(latitude, longitude, matchedAddress));
        } catch (Exception exception) {
            throw new ExternalApiException("카카오 지오코딩 파싱 실패: " + exception.getMessage());
        }
    }
}
