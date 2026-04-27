package com.bridgework.sync.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.dto.PublicDataApiPageResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;

class PublicDataApiClientTest {

    private MockWebServer mockWebServer;
    private PublicDataApiClient publicDataApiClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        BridgeWorkSyncProperties properties = new BridgeWorkSyncProperties();
        properties.setRequestTimeout(Duration.ofSeconds(3));

        publicDataApiClient = new PublicDataApiClient(
                WebClient.builder().build(),
                new ObjectMapper(),
                properties,
                Mockito.mock(KricStationCodeLoader.class)
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchPage_whenArrayResponse_thenParsesItemsAndHasNext() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": {
                            "body": {
                              "items": {
                                "item": [
                                  {"id": "A-1", "name": "테스트1"},
                                  {"id": "A-2", "name": "테스트2"}
                                ]
                              },
                              "totalCount": 3
                            }
                          }
                        }
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = sourceConfig("id", 2);
        PublicDataApiPageResponseDto response = publicDataApiClient.fetchPage(sourceConfig, 1);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).externalId()).isEqualTo("A-1");
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void fetchPage_whenIdFieldMissing_thenUsesPayloadHash() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": {
                            "body": {
                              "items": {
                                "item": {"name": "해시키테스트"}
                              },
                              "totalCount": 1
                            }
                          }
                        }
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = sourceConfig("missingId", 1);
        PublicDataApiPageResponseDto response = publicDataApiClient.fetchPage(sourceConfig, 1);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).externalId()).hasSize(64);
        assertThat(response.hasNext()).isFalse();
    }

    private BridgeWorkSyncProperties.SourceConfig sourceConfig(String itemIdField, int pageSize) {
        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.KEPAD_RECRUITMENT);
        sourceConfig.setBaseUrl(mockWebServer.url("/api").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(pageSize);
        sourceConfig.setMaxPages(1);
        sourceConfig.setItemIdField(itemIdField);
        return sourceConfig;
    }
}
