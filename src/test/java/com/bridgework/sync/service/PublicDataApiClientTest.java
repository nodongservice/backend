package com.bridgework.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.dto.PublicDataApiPageResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
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
    void fetchPage_whenBodyItemsIsArray_thenParsesItems() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": {
                            "body": {
                              "items": [
                                {"id": "B-1", "name": "센터1"},
                                {"id": "B-2", "name": "센터2"}
                              ],
                              "totalCount": 2
                            }
                          }
                        }
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = sourceConfig("id", 1000);
        PublicDataApiPageResponseDto response = publicDataApiClient.fetchPage(sourceConfig, 1);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).externalId()).isEqualTo("B-1");
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void fetchPage_whenTrafficLightXmlResponse_thenParsesItemsAndHasNext() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml")
                .setBody("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <response>
                          <header>
                            <resultCode>00</resultCode>
                            <resultMsg>NORMAL SERVICE.</resultMsg>
                          </header>
                          <body>
                            <items>
                              <item>
                                <tfclghtManageNo>TL-1</tfclghtManageNo>
                                <ctprvnNm>서울특별시</ctprvnNm>
                              </item>
                              <item>
                                <tfclghtManageNo>TL-2</tfclghtManageNo>
                                <ctprvnNm>경기도</ctprvnNm>
                              </item>
                            </items>
                            <numOfRows>2</numOfRows>
                            <pageNo>1</pageNo>
                            <totalCount>3</totalCount>
                          </body>
                        </response>
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.NATIONWIDE_TRAFFIC_LIGHT);
        sourceConfig.setBaseUrl(mockWebServer.url("/traffic").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(2);
        sourceConfig.setMaxPages(1);
        sourceConfig.setQueryParams(Map.of("type", "xml"));

        PublicDataApiPageResponseDto response = publicDataApiClient.fetchPage(sourceConfig, 1);
        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void fetchPage_whenCrosswalkXmlResponse_thenParsesItemsAndHasNext() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml")
                .setBody("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <response>
                          <header>
                            <resultCode>00</resultCode>
                            <resultMsg>NORMAL SERVICE.</resultMsg>
                          </header>
                          <body>
                            <items>
                              <item>
                                <crswlkManageNo>CW-1</crswlkManageNo>
                                <ctprvnNm>서울특별시</ctprvnNm>
                              </item>
                              <item>
                                <crswlkManageNo>CW-2</crswlkManageNo>
                                <ctprvnNm>경기도</ctprvnNm>
                              </item>
                            </items>
                            <numOfRows>2</numOfRows>
                            <pageNo>1</pageNo>
                            <totalCount>3</totalCount>
                          </body>
                        </response>
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.NATIONWIDE_CROSSWALK);
        sourceConfig.setBaseUrl(mockWebServer.url("/crosswalk").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(2);
        sourceConfig.setMaxPages(1);
        sourceConfig.setItemIdField("crswlkManageNo");
        sourceConfig.setQueryParams(Map.of("type", "xml"));

        PublicDataApiPageResponseDto response = publicDataApiClient.fetchPage(sourceConfig, 1);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).externalId()).isEqualTo("CW-1");
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

    @Test
    void fetchPage_whenServiceKeyIsDecodingForm_thenEncodesExactlyOnce() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": {
                            "body": {
                              "items": {"item": {"id": "A-1"}},
                              "totalCount": 1
                            }
                          }
                        }
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = sourceConfig("id", 1);
        sourceConfig.setServiceKey("abc/def+ghi==");
        publicDataApiClient.fetchPage(sourceConfig, 1);

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getRequestUrl().toString()).contains("serviceKey=abc%2Fdef%2Bghi%3D%3D");
        assertThat(request.getRequestUrl().toString()).doesNotContain("%252F");
        assertThat(request.getRequestUrl().toString()).doesNotContain("%252B");
        assertThat(request.getRequestUrl().toString()).doesNotContain("%253D");
    }

    @Test
    void fetchPage_whenServiceKeyIsEncodingForm_thenDoesNotDoubleEncode() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": {
                            "body": {
                              "items": {"item": {"id": "A-1"}},
                              "totalCount": 1
                            }
                          }
                        }
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = sourceConfig("id", 1);
        sourceConfig.setServiceKey("abc%2Fdef%2Bghi%3D%3D");
        publicDataApiClient.fetchPage(sourceConfig, 1);

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getRequestUrl().toString()).contains("serviceKey=abc%2Fdef%2Bghi%3D%3D");
        assertThat(request.getRequestUrl().toString()).doesNotContain("%252F");
        assertThat(request.getRequestUrl().toString()).doesNotContain("%252B");
        assertThat(request.getRequestUrl().toString()).doesNotContain("%253D");
    }

    @Test
    void resolveLatestDataGoFileDataVersion_whenUddiAndNumericCandidatesExist_thenSelectsUddi() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <input type="hidden" id="publicDataPk" name="publicDataPk" value="15044262"/>
                            <input type="hidden" id="publicDataDetailPk" name="publicDataDetailPk" value="uddi:54994cec-189c-4158-a0c0-c23bde567cad"/>
                            <script>
                              var legacy = "publicDataDetailPk=09073736";
                            </script>
                          </body>
                        </html>
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <div id="swagger-container">
                              2024-10-28
                              https://api.odcloud.kr/api/15044262/v1/09073736
                              https://api.odcloud.kr/api/15044262/v1/uddi:54994cec-189c-4158-a0c0-c23bde567cad
                            </div>
                          </body>
                        </html>
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.SEOUL_WHEELCHAIR_LIFT);
        sourceConfig.setBaseUrl(mockWebServer.url("/filedata").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(1000);
        sourceConfig.setMaxPages(1);

        Object version = ReflectionTestUtils.invokeMethod(
                publicDataApiClient,
                "resolveLatestDataGoFileDataVersion",
                sourceConfig
        );

        String detailPk = (String) version.getClass().getMethod("publicDataDetailPk").invoke(version);
        assertThat(detailPk).isEqualTo("uddi:54994cec-189c-4158-a0c0-c23bde567cad");
    }

    @Test
    void resolveLatestDataGoFileDataVersion_whenOtherDatasetDetailKeyExists_thenKeepsOwnDatasetDetailKey() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <input type="hidden" id="publicDataPk" name="publicDataPk" value="15044262"/>
                            <input type="hidden" id="publicDataDetailPk" name="publicDataDetailPk" value="uddi:54994cec-189c-4158-a0c0-c23bde567cad"/>
                            <a href="https://api.odcloud.kr/api/15041686/v1/uddi:4dbe1903-4a2f-4d8b-af48-b07985732550">other-dataset</a>
                          </body>
                        </html>
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <div id="swagger-container">
                              2024-10-28
                              https://api.odcloud.kr/api/15041686/v1/uddi:4dbe1903-4a2f-4d8b-af48-b07985732550
                              https://api.odcloud.kr/api/15044262/v1/uddi:54994cec-189c-4158-a0c0-c23bde567cad
                            </div>
                          </body>
                        </html>
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.SEOUL_WHEELCHAIR_LIFT);
        sourceConfig.setBaseUrl(mockWebServer.url("/filedata").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(1000);
        sourceConfig.setMaxPages(1);

        Object version = ReflectionTestUtils.invokeMethod(
                publicDataApiClient,
                "resolveLatestDataGoFileDataVersion",
                sourceConfig
        );

        String detailPk = (String) version.getClass().getMethod("publicDataDetailPk").invoke(version);
        assertThat(detailPk).isEqualTo("uddi:54994cec-189c-4158-a0c0-c23bde567cad");
    }

    @Test
    void resolveLatestDataGoFileDataVersion_whenSameDatasetEndpointHasWrongDetail_thenUsesHiddenInputForSeoulLift() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <input type="hidden" id="publicDataPk" name="publicDataPk" value="15044262"/>
                            <input type="hidden" id="publicDataDetailPk" name="publicDataDetailPk" value="uddi:54994cec-189c-4158-a0c0-c23bde567cad"/>
                            <a href="https://api.odcloud.kr/api/15044262/v1/uddi:4dbe1903-4a2f-4d8b-af48-b07985732550">stale-endpoint</a>
                          </body>
                        </html>
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <div id="swagger-container">
                              2024-10-28
                              https://api.odcloud.kr/api/15044262/v1/uddi:54994cec-189c-4158-a0c0-c23bde567cad
                              https://api.odcloud.kr/api/15044262/v1/uddi:4dbe1903-4a2f-4d8b-af48-b07985732550
                            </div>
                          </body>
                        </html>
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.SEOUL_WHEELCHAIR_LIFT);
        sourceConfig.setBaseUrl(mockWebServer.url("/filedata").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(1000);
        sourceConfig.setMaxPages(1);

        Object version = ReflectionTestUtils.invokeMethod(
                publicDataApiClient,
                "resolveLatestDataGoFileDataVersion",
                sourceConfig
        );

        String detailPk = (String) version.getClass().getMethod("publicDataDetailPk").invoke(version);
        assertThat(detailPk).isEqualTo("uddi:54994cec-189c-4158-a0c0-c23bde567cad");
    }

    @Test
    void resolveLatestDataGoFileDataVersion_whenPageHasDuplicateIds_thenPrefersFrmFileValues() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <section id="recommend">
                              <input type="hidden" id="publicDataPk" value="15041686"/>
                              <input type="hidden" id="publicDataDetailPk" value="uddi:4dbe1903-4a2f-4d8b-af48-b07985732550"/>
                            </section>
                            <form id="frmFile" name="frmFile">
                              <input type="hidden" id="publicDataPk" name="publicDataPk" value="15044262"/>
                              <input type="hidden" id="publicDataDetailPk" name="publicDataDetailPk" value="uddi:54994cec-189c-4158-a0c0-c23bde567cad"/>
                            </form>
                          </body>
                        </html>
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <div id="swagger-container">
                              2024-10-28
                              https://api.odcloud.kr/api/15044262/v1/uddi:54994cec-189c-4158-a0c0-c23bde567cad
                              https://api.odcloud.kr/api/15041686/v1/uddi:4dbe1903-4a2f-4d8b-af48-b07985732550
                            </div>
                          </body>
                        </html>
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.SEOUL_WHEELCHAIR_LIFT);
        sourceConfig.setBaseUrl(mockWebServer.url("/filedata").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(1000);
        sourceConfig.setMaxPages(1);

        Object version = ReflectionTestUtils.invokeMethod(
                publicDataApiClient,
                "resolveLatestDataGoFileDataVersion",
                sourceConfig
        );

        String publicDataPk = (String) version.getClass().getMethod("publicDataPk").invoke(version);
        String detailPk = (String) version.getClass().getMethod("publicDataDetailPk").invoke(version);
        assertThat(publicDataPk).isEqualTo("15044262");
        assertThat(detailPk).isEqualTo("uddi:54994cec-189c-4158-a0c0-c23bde567cad");
    }

    @Test
    void resolveLatestDataGoFileDataVersion_whenSwaggerContainerHasEscapedEndpoint_thenUsesLatestSwaggerCandidate() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <form id="frmFile" name="frmFile">
                              <input type="hidden" id="publicDataPk" name="publicDataPk" value="15067528"/>
                              <input type="hidden" id="publicDataDetailPk" name="publicDataDetailPk" value="uddi:f74b9799-9db1-4754-a5d0-b66e2ae705f3"/>
                            </form>
                          </body>
                        </html>
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <div id="swagger-container">
                              2024-10-28
                              <script>
                                const endpoint = "https:\\/\\/api.odcloud.kr\\/api\\/15067528\\/v1\\/uddi:ed6d9b4d-96cc-4e2a-85b3-98769395fc87";
                              </script>
                            </div>
                          </body>
                        </html>
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.NATIONWIDE_BUS_STOP);
        sourceConfig.setBaseUrl(mockWebServer.url("/fileData.do").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(1000);
        sourceConfig.setMaxPages(1);

        Object version = ReflectionTestUtils.invokeMethod(
                publicDataApiClient,
                "resolveLatestDataGoFileDataVersion",
                sourceConfig
        );

        String detailPk = (String) version.getClass().getMethod("publicDataDetailPk").invoke(version);
        String revisionKey = (String) version.getClass().getMethod("revisionKey").invoke(version);
        assertThat(detailPk).isEqualTo("uddi:ed6d9b4d-96cc-4e2a-85b3-98769395fc87");
        assertThat(revisionKey).isEqualTo("2024-10-28|15067528");
    }

    @Test
    void resolveLatestDataGoFileDataVersion_whenSwaggerHasRelativeEndpointAndCompactDate_thenSelectsLatest() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <form id="frmFile" name="frmFile">
                              <input type="hidden" id="publicDataPk" name="publicDataPk" value="15067528"/>
                              <input type="hidden" id="publicDataDetailPk" name="publicDataDetailPk" value="uddi:f74b9799-9db1-4754-a5d0-b66e2ae705f3"/>
                            </form>
                          </body>
                        </html>
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <div id="swagger-container">
                              국토교통부_전국 버스정류장 위치정보_20241028
                              <script>
                                const relativeApi = "/api/15067528/v1/uddi%3Aed6d9b4d-96cc-4e2a-85b3-98769395fc87";
                                const escapedApi = "\\u002fapi\\u002f15067528\\u002fv1\\u002fuddi:ed6d9b4d-96cc-4e2a-85b3-98769395fc87";
                              </script>
                            </div>
                          </body>
                        </html>
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.NATIONWIDE_BUS_STOP);
        sourceConfig.setBaseUrl(mockWebServer.url("/fileData.do").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(1000);
        sourceConfig.setMaxPages(1);

        Object version = ReflectionTestUtils.invokeMethod(
                publicDataApiClient,
                "resolveLatestDataGoFileDataVersion",
                sourceConfig
        );

        String detailPk = (String) version.getClass().getMethod("publicDataDetailPk").invoke(version);
        String revisionKey = (String) version.getClass().getMethod("revisionKey").invoke(version);
        assertThat(detailPk).isEqualTo("uddi:ed6d9b4d-96cc-4e2a-85b3-98769395fc87");
        assertThat(revisionKey).isEqualTo("2024-10-28|15067528");
    }

    @Test
    void resolveLatestDataGoFileDataVersion_whenSwaggerEndpointIsInExternalScript_thenFindsCandidate() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <form id="frmFile" name="frmFile">
                              <input type="hidden" id="publicDataPk" name="publicDataPk" value="15044262"/>
                              <input type="hidden" id="publicDataDetailPk" name="publicDataDetailPk" value="uddi:legacy-old-value"/>
                            </form>
                          </body>
                        </html>
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <div id="swagger-container">서울교통공사_휠체어리프트 설치현황_20260211</div>
                            <script src="/assets/swagger-source.js"></script>
                          </body>
                        </html>
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/javascript")
                .setBody("""
                        const apiPath = "/api/15044262/v1/uddi:54994cec-189c-4158-a0c0-c23bde567cad";
                        """));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.SEOUL_WHEELCHAIR_LIFT);
        sourceConfig.setBaseUrl(mockWebServer.url("/fileData.do").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(1000);
        sourceConfig.setMaxPages(1);

        Object version = ReflectionTestUtils.invokeMethod(
                publicDataApiClient,
                "resolveLatestDataGoFileDataVersion",
                sourceConfig
        );

        String detailPk = (String) version.getClass().getMethod("publicDataDetailPk").invoke(version);
        String revisionKey = (String) version.getClass().getMethod("revisionKey").invoke(version);
        assertThat(detailPk).isEqualTo("uddi:54994cec-189c-4158-a0c0-c23bde567cad");
        assertThat(revisionKey).isEqualTo("2026-02-11|15044262");
    }

    @Test
    void resolveLatestDataGoFileDataVersion_whenNoApiCandidateAnywhere_thenThrows() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("""
                        <html>
                          <body>
                            <form id="frmFile" name="frmFile">
                              <input type="hidden" id="publicDataPk" name="publicDataPk" value="15067528"/>
                              <input type="hidden" id="publicDataDetailPk" name="publicDataDetailPk" value="uddi:f74b9799-9db1-4754-a5d0-b66e2ae705f3"/>
                            </form>
                          </body>
                        </html>
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body><div id=\"swagger-container\">endpoint 없음</div></body></html>"));

        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(PublicDataSourceType.NATIONWIDE_BUS_STOP);
        sourceConfig.setBaseUrl(mockWebServer.url("/fileData.do").toString());
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(1000);
        sourceConfig.setMaxPages(1);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                publicDataApiClient,
                "resolveLatestDataGoFileDataVersion",
                sourceConfig
        )).hasMessageContaining("파일데이터 최신 OpenAPI 후보 추출 실패");
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
