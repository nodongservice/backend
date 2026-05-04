package com.bridgework.recommend.service;

import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.recommend.config.BridgeWorkRecommendProperties;
import com.bridgework.recommend.exception.RecommendDomainException;
import java.util.LinkedHashMap;
import java.util.Map;
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
        payload.put("profileId", profile.profileId());
        payload.put("profile", profile);
        return payload;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}

