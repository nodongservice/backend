package com.bridgework.sync.normalized;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

class NaverGeocodingServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildQueryCandidates_whenAddressHasDetailedBuilding_thenAddsRoadAndLocalityFallback() {
        NaverGeocodingService service = new NaverGeocodingService(WebClient.builder().build(), new ObjectMapper());

        List<String> candidates = (List<String>) ReflectionTestUtils.invokeMethod(
                service,
                "buildQueryCandidates",
                "(51558) 경상남도 창원시 성산구 웅남로 316 (웅남동) 창원지식산업센터 지원시설동 404호"
        );

        assertThat(candidates).contains(
                "경상남도 창원시 성산구 웅남로 316",
                "경상남도 창원시 성산구 웅남로",
                "경상남도 창원시 성산구 웅남동"
        );
    }
}
