package com.bridgework.auth.service;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.entity.SocialProvider;
import com.bridgework.auth.exception.SocialLoginFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class SocialOAuthService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final BridgeWorkAuthProperties authProperties;

    public SocialOAuthService(WebClient webClient,
                              ObjectMapper objectMapper,
                              BridgeWorkAuthProperties authProperties) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.authProperties = authProperties;
    }

    public SocialUserProfile fetchUserProfile(SocialProvider provider,
                                              String code,
                                              String redirectUri,
                                              String state) {
        BridgeWorkAuthProperties.Provider providerProperties = getProviderProperties(provider);
        validateProviderConfiguration(provider, providerProperties);
        String resolvedRedirectUri = StringUtils.hasText(redirectUri) ? redirectUri : providerProperties.getRedirectUri();

        String accessToken = exchangeAccessToken(provider, providerProperties, code, resolvedRedirectUri, state);
        String userInfoResponse = requestUserInfo(providerProperties, accessToken);

        return switch (provider) {
            case KAKAO -> parseKakaoUserInfo(userInfoResponse);
            case NAVER -> parseNaverUserInfo(userInfoResponse);
        };
    }

    private String exchangeAccessToken(SocialProvider provider,
                                       BridgeWorkAuthProperties.Provider providerProperties,
                                       String code,
                                       String redirectUri,
                                       String state) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", providerProperties.getClientId());
        formData.add("client_secret", providerProperties.getClientSecret());
        formData.add("code", code);
        formData.add("redirect_uri", redirectUri);

        if (provider == SocialProvider.NAVER && StringUtils.hasText(state)) {
            formData.add("state", state);
        }

        try {
            String responseBody = webClient.post()
                    .uri(providerProperties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(formData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            if (!StringUtils.hasText(responseBody)) {
                throw new SocialLoginFailedException("소셜 액세스 토큰 응답이 비어 있습니다.");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String accessToken = root.path("access_token").asText("");
            if (!StringUtils.hasText(accessToken)) {
                throw new SocialLoginFailedException("소셜 액세스 토큰을 확인할 수 없습니다.");
            }
            return accessToken;
        } catch (SocialLoginFailedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SocialLoginFailedException("소셜 액세스 토큰 발급에 실패했습니다.", exception);
        }
    }

    private String requestUserInfo(BridgeWorkAuthProperties.Provider providerProperties, String accessToken) {
        try {
            String responseBody = webClient.get()
                    .uri(providerProperties.getUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            if (!StringUtils.hasText(responseBody)) {
                throw new SocialLoginFailedException("소셜 사용자 정보 응답이 비어 있습니다.");
            }
            return responseBody;
        } catch (SocialLoginFailedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SocialLoginFailedException("소셜 사용자 정보 조회에 실패했습니다.", exception);
        }
    }

    private SocialUserProfile parseKakaoUserInfo(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String providerUserId = root.path("id").asText("");
            JsonNode kakaoAccount = root.path("kakao_account");
            String email = asNullableText(kakaoAccount.path("email"));
            String name = asNullableText(kakaoAccount.path("name"));

            if (!StringUtils.hasText(name)) {
                JsonNode profileNode = kakaoAccount.path("profile");
                name = asNullableText(profileNode.path("nickname"));
            }

            if (!StringUtils.hasText(providerUserId)) {
                throw new SocialLoginFailedException("카카오 사용자 식별자를 확인할 수 없습니다.");
            }
            if (!StringUtils.hasText(email)) {
                throw new SocialLoginFailedException("카카오 계정 이메일 정보를 확인할 수 없습니다. 이메일 제공 동의 후 다시 시도해 주세요.");
            }

            return new SocialUserProfile(SocialProvider.KAKAO, providerUserId, email, name);
        } catch (SocialLoginFailedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SocialLoginFailedException("카카오 사용자 정보 파싱에 실패했습니다.", exception);
        }
    }

    private SocialUserProfile parseNaverUserInfo(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode responseNode = root.path("response");
            String providerUserId = responseNode.path("id").asText("");
            String email = asNullableText(responseNode.path("email"));
            String name = asNullableText(responseNode.path("name"));

            if (!StringUtils.hasText(providerUserId)) {
                throw new SocialLoginFailedException("네이버 사용자 식별자를 확인할 수 없습니다.");
            }
            if (!StringUtils.hasText(email)) {
                throw new SocialLoginFailedException("네이버 계정 이메일 정보를 확인할 수 없습니다. 이메일 제공 동의 후 다시 시도해 주세요.");
            }

            return new SocialUserProfile(SocialProvider.NAVER, providerUserId, email, name);
        } catch (SocialLoginFailedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SocialLoginFailedException("네이버 사용자 정보 파싱에 실패했습니다.", exception);
        }
    }

    private BridgeWorkAuthProperties.Provider getProviderProperties(SocialProvider provider) {
        return switch (provider) {
            case KAKAO -> authProperties.getOauth2().getKakao();
            case NAVER -> authProperties.getOauth2().getNaver();
        };
    }

    private void validateProviderConfiguration(SocialProvider provider, BridgeWorkAuthProperties.Provider providerProperties) {
        if (!StringUtils.hasText(providerProperties.getClientId())
                || !StringUtils.hasText(providerProperties.getClientSecret())
                || !StringUtils.hasText(providerProperties.getTokenUri())
                || !StringUtils.hasText(providerProperties.getUserInfoUri())
                || !StringUtils.hasText(providerProperties.getRedirectUri())) {
            throw new SocialLoginFailedException(provider.name() + " OAuth 설정이 누락되었습니다.");
        }
    }

    private String asNullableText(JsonNode node) {
        String value = node.asText("").trim();
        return value.isBlank() ? null : value;
    }
}
