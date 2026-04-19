package com.bridgework.sync.service;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.dto.PublicDataApiItemDto;
import com.bridgework.sync.dto.PublicDataApiPageResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.exception.ExternalApiException;
import com.bridgework.sync.exception.PayloadParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.util.retry.Retry;

@Component
public class PublicDataApiClient {

    private static final Logger log = LoggerFactory.getLogger(PublicDataApiClient.class);
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int API_RETRY_COUNT = 3;
    private static final Duration API_RETRY_BACKOFF = Duration.ofMillis(500);
    private static final String DEFAULT_ITEMS_POINTER = "/response/body/items/item";
    private static final String DEFAULT_TOTAL_COUNT_POINTER = "/response/body/totalCount";
    private static final Pattern PUBLIC_DATA_PK_PATTERN = Pattern.compile("id=\"publicDataPk\"[^>]*value=\"([^\"]+)\"");
    private static final Pattern PUBLIC_DATA_DETAIL_PK_PATTERN =
            Pattern.compile("id=\"publicDataDetailPk\"[^>]*value=\"([^\"]+)\"");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;
    private final BridgeWorkSyncProperties syncProperties;
    private final KricStationCodeLoader kricStationCodeLoader;

    public PublicDataApiClient(WebClient webClient,
                               ObjectMapper objectMapper,
                               BridgeWorkSyncProperties syncProperties,
                               KricStationCodeLoader kricStationCodeLoader) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.xmlMapper = XmlMapper.builder().defaultUseWrapper(false).build();
        this.syncProperties = syncProperties;
        this.kricStationCodeLoader = kricStationCodeLoader;
    }

    public PublicDataApiPageResponseDto fetchPage(BridgeWorkSyncProperties.SourceConfig sourceConfig, int pageNo) {
        PublicDataSourceType sourceType = sourceConfig.getSourceType();

        if (sourceType == PublicDataSourceType.SEOUL_WHEELCHAIR_LIFT) {
            return fetchSeoulWheelchairLiftPage(sourceConfig, pageNo);
        }
        if (sourceType == PublicDataSourceType.RAIL_WHEELCHAIR_LIFT) {
            return fetchRailWheelchairLiftPage(sourceConfig, pageNo);
        }
        if (sourceType == PublicDataSourceType.VOCATIONAL_TRAINING) {
            return fetchVocationalTrainingPage(sourceConfig, pageNo);
        }
        if (sourceType == PublicDataSourceType.JOBSEEKER_COMPETENCY_PROGRAM) {
            return fetchJobseekerCompetencyProgramPage(sourceConfig, pageNo);
        }

        String requestUri = buildRequestUri(sourceConfig, pageNo);
        String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            validateApiResultOrThrow(rootNode, sourceConfig.getSourceType());
            JsonNode itemsNode = resolveItemsNode(rootNode, sourceConfig.getItemsJsonPointer());
            List<PublicDataApiItemDto> items = mapItems(itemsNode, sourceConfig);

            int totalCount = resolveTotalCount(rootNode, sourceConfig.getTotalCountJsonPointer()).orElse(-1);
            boolean hasNext = hasNextPage(totalCount, pageNo, sourceConfig.getPageSize(), items.size());
            log.info("[COUNT] source={} page={} detected={} hasNext={}",
                    sourceConfig.getSourceType(),
                    pageNo,
                    items.size(),
                    hasNext);

            return new PublicDataApiPageResponseDto(items, hasNext);
        } catch (JsonProcessingException exception) {
            throw new PayloadParseException(
                    "공공데이터 응답 파싱 실패: " + sourceConfig.getSourceType(),
                    exception
            );
        }
    }

    private PublicDataApiPageResponseDto fetchSeoulWheelchairLiftPage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        if (pageNo > 1) {
            return new PublicDataApiPageResponseDto(List.of(), false);
        }

        String fileDataPageBody = fetchBody(sourceConfig.getBaseUrl(), sourceConfig.getSourceType());
        String publicDataPk = extractFileDataField(fileDataPageBody, PUBLIC_DATA_PK_PATTERN, "publicDataPk");
        String publicDataDetailPk = extractFileDataField(fileDataPageBody, PUBLIC_DATA_DETAIL_PK_PATTERN, "publicDataDetailPk");

        List<String> stationNames = kricStationCodeLoader.loadSeoulStationNames();
        List<PublicDataApiItemDto> aggregatedItems = new ArrayList<>();
        Set<String> dedupedExternalIds = new LinkedHashSet<>();

        for (String stationName : stationNames) {
            String requestUri = buildSeoulWheelchairLiftRequestUri(
                    publicDataPk,
                    publicDataDetailPk,
                    sourceConfig,
                    stationName
            );
            String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());
            List<PublicDataApiItemDto> stationItems = parseSeoulWheelchairLiftItems(responseBody, sourceConfig);
            log.info("[COUNT] source={} station={} detected={}",
                    sourceConfig.getSourceType(),
                    stationName,
                    stationItems.size());

            for (PublicDataApiItemDto stationItem : stationItems) {
                if (dedupedExternalIds.add(stationItem.externalId())) {
                    aggregatedItems.add(stationItem);
                }
            }
        }

        if (!aggregatedItems.isEmpty()) {
            return new PublicDataApiPageResponseDto(aggregatedItems, false);
        }

        // 역사명 필터가 불일치하는 경우를 대비해 전체 조회를 1회 수행한다.
        String fallbackRequestUri = buildSeoulWheelchairLiftRequestUri(publicDataPk, publicDataDetailPk, sourceConfig, null);
        String fallbackResponseBody = fetchBody(fallbackRequestUri, sourceConfig.getSourceType());
        List<PublicDataApiItemDto> fallbackItems = parseSeoulWheelchairLiftItems(fallbackResponseBody, sourceConfig);
        log.info("[COUNT] source={} fallback=all detected={}", sourceConfig.getSourceType(), fallbackItems.size());
        return new PublicDataApiPageResponseDto(fallbackItems, false);
    }

    private PublicDataApiPageResponseDto fetchRailWheelchairLiftPage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        if (pageNo > 1) {
            return new PublicDataApiPageResponseDto(List.of(), false);
        }

        List<KricStationCodeLoader.StationReference> stationReferences = kricStationCodeLoader.loadRailStationReferences();
        List<PublicDataApiItemDto> aggregatedItems = new ArrayList<>();
        Set<String> dedupedExternalIds = new LinkedHashSet<>();

        for (KricStationCodeLoader.StationReference stationReference : stationReferences) {
            String requestUri = buildRailWheelchairLiftRequestUri(sourceConfig, stationReference);
            String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());

            try {
                JsonNode rootNode = objectMapper.readTree(responseBody);
                validateApiResultOrThrow(rootNode, sourceConfig.getSourceType());
                JsonNode itemsNode = resolveRailItemsNode(rootNode, sourceConfig.getItemsJsonPointer());
                List<PublicDataApiItemDto> stationItems = mapRailItems(itemsNode, stationReference);
                log.info("[COUNT] source={} railOprIsttCd={} lnCd={} stinCd={} detected={}",
                        sourceConfig.getSourceType(),
                        stationReference.railOprIsttCd(),
                        stationReference.lnCd(),
                        stationReference.stinCd(),
                        stationItems.size());

                for (PublicDataApiItemDto stationItem : stationItems) {
                    if (dedupedExternalIds.add(stationItem.externalId())) {
                        aggregatedItems.add(stationItem);
                    }
                }
            } catch (JsonProcessingException exception) {
                throw new PayloadParseException(
                        "국토교통부 역사별 휠체어리프트 응답 파싱 실패: "
                                + stationReference.railOprIsttCd() + "/" + stationReference.lnCd() + "/" + stationReference.stinCd(),
                        exception
                );
            }
        }

        return new PublicDataApiPageResponseDto(aggregatedItems, false);
    }

    private PublicDataApiPageResponseDto fetchVocationalTrainingPage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        String requestUri = buildVocationalTrainingRequestUri(sourceConfig, pageNo);
        String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());
        JsonNode rootNode = parseXml(responseBody, "직업훈련 API 응답 파싱 실패");

        JsonNode itemsNode = resolveItemsNode(rootNode, sourceConfig.getItemsJsonPointer());
        List<PublicDataApiItemDto> items = mapItems(itemsNode, sourceConfig);
        int totalCount = resolveTotalCount(rootNode, sourceConfig.getTotalCountJsonPointer()).orElse(-1);
        boolean hasNext = hasNextPage(totalCount, pageNo, sourceConfig.getPageSize(), items.size());
        log.info("[COUNT] source={} page={} detected={} hasNext={}",
                sourceConfig.getSourceType(),
                pageNo,
                items.size(),
                hasNext);
        return new PublicDataApiPageResponseDto(items, hasNext);
    }

    private PublicDataApiPageResponseDto fetchJobseekerCompetencyProgramPage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        if (pageNo > 1) {
            return new PublicDataApiPageResponseDto(List.of(), false);
        }

        LocalDate startDate = LocalDate.now(SEOUL_ZONE_ID);
        LocalDate endDate = startDate.plusMonths(1);
        List<PublicDataApiItemDto> aggregatedItems = new ArrayList<>();
        Set<String> dedupedExternalIds = new LinkedHashSet<>();

        for (LocalDate currentDate = startDate; !currentDate.isAfter(endDate); currentDate = currentDate.plusDays(1)) {
            String pgmStdt = currentDate.format(YYYYMMDD_FORMATTER);

            for (int datePageNo = 1; datePageNo <= sourceConfig.getMaxPages(); datePageNo++) {
                String requestUri = buildJobseekerCompetencyProgramRequestUri(sourceConfig, datePageNo, pgmStdt);
                String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());
                JsonNode rootNode = parseXml(
                        responseBody,
                        "구직자 취업역량 강화프로그램 API 응답 파싱 실패: pgmStdt=" + pgmStdt + ", startPage=" + datePageNo
                );

                if (rootNode.at("/empPgmSchdInviteList/messageCd").asText("").equals("006")) {
                    log.info("[COUNT] source={} pgmStdt={} page={} detected=0 (messageCd=006)",
                            sourceConfig.getSourceType(),
                            pgmStdt,
                            datePageNo);
                    break;
                }

                JsonNode itemsNode = resolveItemsNode(rootNode, sourceConfig.getItemsJsonPointer());
                List<PublicDataApiItemDto> datePageItems = mapItems(itemsNode, sourceConfig);
                log.info("[COUNT] source={} pgmStdt={} page={} detected={}",
                        sourceConfig.getSourceType(),
                        pgmStdt,
                        datePageNo,
                        datePageItems.size());
                if (datePageItems.isEmpty()) {
                    break;
                }

                for (PublicDataApiItemDto datePageItem : datePageItems) {
                    if (dedupedExternalIds.add(datePageItem.externalId())) {
                        aggregatedItems.add(datePageItem);
                    }
                }

                int totalCount = resolveTotalCount(rootNode, sourceConfig.getTotalCountJsonPointer()).orElse(-1);
                if (!hasNextPage(totalCount, datePageNo, sourceConfig.getPageSize(), datePageItems.size())) {
                    break;
                }
            }
        }

        return new PublicDataApiPageResponseDto(aggregatedItems, false);
    }

    private String buildRequestUri(BridgeWorkSyncProperties.SourceConfig sourceConfig, int pageNo) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("serviceKey", sourceConfig.getServiceKey())
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", sourceConfig.getPageSize());

        appendJsonResponseTypeParam(builder, sourceConfig.getQueryParams());
        applyQueryParams(builder, sourceConfig.getQueryParams());
        return builder.build(true).toUriString();
    }

    private String buildSeoulWheelchairLiftRequestUri(String publicDataPk,
                                                      String publicDataDetailPk,
                                                      BridgeWorkSyncProperties.SourceConfig sourceConfig,
                                                      String stationName) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl("https://api.odcloud.kr/api/" + publicDataPk + "/v1/" + publicDataDetailPk)
                .queryParam("serviceKey", sourceConfig.getServiceKey())
                .queryParam("page", 1)
                .queryParam("perPage", sourceConfig.getPageSize())
                .queryParam("returnType", "JSON");

        if (stationName != null && !stationName.isBlank()) {
            // fileData 변환 API는 컬럼명 기반 필터를 지원하므로 역명으로 전체 역을 순회 조회한다.
            builder.queryParam("역명", stationName.trim());
        }

        applyQueryParams(builder, sourceConfig.getQueryParams());
        return builder.build(true).toUriString();
    }

    private List<PublicDataApiItemDto> parseSeoulWheelchairLiftItems(
            String responseBody,
            BridgeWorkSyncProperties.SourceConfig sourceConfig
    ) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode itemsNode = rootNode.path("data");
            return mapItems(itemsNode, sourceConfig);
        } catch (JsonProcessingException exception) {
            throw new PayloadParseException("서울교통공사 파일데이터 응답 파싱 실패", exception);
        }
    }

    private String buildRailWheelchairLiftRequestUri(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            KricStationCodeLoader.StationReference stationReference
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("serviceKey", sourceConfig.getServiceKey())
                .queryParam("railOprIsttCd", stationReference.railOprIsttCd())
                .queryParam("lnCd", stationReference.lnCd())
                .queryParam("stinCd", stationReference.stinCd());

        applyQueryParams(builder, sourceConfig.getQueryParams());
        return builder.build(true).toUriString();
    }

    private String buildVocationalTrainingRequestUri(BridgeWorkSyncProperties.SourceConfig sourceConfig, int pageNo) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("authKey", sourceConfig.getServiceKey())
                .queryParam("returnType", "XML")
                .queryParam("pageNum", pageNo)
                .queryParam("pageSize", sourceConfig.getPageSize());

        applyQueryParams(builder, sourceConfig.getQueryParams());
        return builder.build(true).toUriString();
    }

    private String buildJobseekerCompetencyProgramRequestUri(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo,
            String pgmStdt
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("authKey", sourceConfig.getServiceKey())
                .queryParam("returnType", "XML")
                .queryParam("startPage", pageNo)
                .queryParam("display", sourceConfig.getPageSize())
                .queryParam("pgmStdt", pgmStdt);

        applyQueryParams(builder, sourceConfig.getQueryParams());
        return builder.build(true).toUriString();
    }

    private void applyQueryParams(UriComponentsBuilder builder, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return;
        }

        queryParams.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                builder.queryParam(key, value);
            }
        });
    }

    private void appendJsonResponseTypeParam(UriComponentsBuilder builder, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            builder.queryParam("_type", "json");
            return;
        }

        boolean hasResponseTypeParam = queryParams.keySet().stream()
                .anyMatch(key -> "type".equalsIgnoreCase(key)
                        || "_type".equalsIgnoreCase(key)
                        || "returnType".equalsIgnoreCase(key));

        if (!hasResponseTypeParam) {
            builder.queryParam("_type", "json");
        }
    }

    private String fetchBody(String requestUri, PublicDataSourceType sourceType) {
        log.info("[HTTP] source={} uri={}", sourceType, requestUri);
        return webClient
                .get()
                .uri(requestUri)
                .retrieve()
                .onStatus(status -> status.value() == 429 || status.is5xxServerError(), clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new ExternalApiException(
                                        "공공데이터 API 호출 실패(재시도 대상, " + sourceType + "): "
                                                + clientResponse.statusCode().value()
                                ))
                )
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new ExternalApiException(
                                        "공공데이터 API 호출 실패(" + sourceType + "): "
                                                + clientResponse.statusCode().value()
                                ))
                )
                .bodyToMono(String.class)
                .timeout(syncProperties.getRequestTimeout())
                .retryWhen(
                        Retry.backoff(API_RETRY_COUNT, API_RETRY_BACKOFF)
                                .filter(this::isRetryableApiException)
                )
                .blockOptional()
                .orElseThrow(() -> new ExternalApiException("공공데이터 API 응답이 비어 있습니다: " + sourceType));
    }

    private boolean isRetryableApiException(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return false;
        }

        String message = throwable.getMessage();
        return message.contains("재시도 대상")
                || message.contains("Connection reset")
                || message.contains("Read timed out")
                || message.contains("connection prematurely closed")
                || message.contains("failed to respond");
    }

    private String extractFileDataField(String body, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new ExternalApiException("파일데이터 페이지에서 " + fieldName + " 추출 실패");
        }
        // fileData 페이지의 식별자를 그대로 사용해야 변환 OpenAPI의 최신 버전 경로를 고정할 수 있다.
        return matcher.group(1);
    }

    private JsonNode resolveItemsNode(JsonNode rootNode, String configuredPointer) {
        String pointer = (configuredPointer == null || configuredPointer.isBlank())
                ? DEFAULT_ITEMS_POINTER
                : configuredPointer;

        JsonNode itemsNode = rootNode.at(pointer);
        if (!itemsNode.isMissingNode() && !itemsNode.isNull()) {
            return itemsNode;
        }

        JsonNode fallbackItems = rootNode.path("response").path("body").path("items").path("item");
        if (!fallbackItems.isMissingNode() && !fallbackItems.isNull()) {
            return fallbackItems;
        }

        return objectMapper.createArrayNode();
    }

    private JsonNode resolveRailItemsNode(JsonNode rootNode, String configuredPointer) {
        JsonNode configuredItemsNode = resolveItemsNode(rootNode, configuredPointer);
        if ((configuredItemsNode.isArray() && configuredItemsNode.size() > 0) || configuredItemsNode.isObject()) {
            return configuredItemsNode;
        }

        JsonNode bodyNode = rootNode.path("body");
        if (!bodyNode.isMissingNode() && !bodyNode.isNull()) {
            return bodyNode;
        }

        JsonNode dataNode = rootNode.path("data");
        if (!dataNode.isMissingNode() && !dataNode.isNull()) {
            return dataNode;
        }

        if (rootNode.isArray()) {
            return rootNode;
        }

        return configuredItemsNode;
    }

    private Optional<Integer> resolveTotalCount(JsonNode rootNode, String configuredPointer) {
        String pointer = (configuredPointer == null || configuredPointer.isBlank())
                ? DEFAULT_TOTAL_COUNT_POINTER
                : configuredPointer;

        JsonNode totalCountNode = rootNode.at(pointer);
        if (!totalCountNode.isMissingNode()) {
            Optional<Integer> parsed = parseInteger(totalCountNode.asText(null));
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        JsonNode fallbackCount = rootNode.path("response").path("body").path("totalCount");
        if (!fallbackCount.isMissingNode()) {
            Optional<Integer> parsed = parseInteger(fallbackCount.asText(null));
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        return Optional.empty();
    }

    private Optional<Integer> parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private List<PublicDataApiItemDto> mapRailItems(
            JsonNode itemsNode,
            KricStationCodeLoader.StationReference stationReference
    ) {
        List<PublicDataApiItemDto> items = new ArrayList<>();

        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                items.add(toRailItem(itemNode, stationReference));
            }
            return items;
        }

        if (itemsNode.isObject()) {
            if (containsRailField(itemsNode)) {
                items.add(toRailItem(itemsNode, stationReference));
                return items;
            }

            for (String nestedKey : List.of("item", "items", "list", "row", "body", "data", "result")) {
                JsonNode nestedNode = itemsNode.path(nestedKey);
                if (nestedNode.isArray()) {
                    for (JsonNode childNode : nestedNode) {
                        items.add(toRailItem(childNode, stationReference));
                    }
                    if (!items.isEmpty()) {
                        return items;
                    }
                } else if (nestedNode.isObject() && containsRailField(nestedNode)) {
                    items.add(toRailItem(nestedNode, stationReference));
                    return items;
                }
            }
        }

        return items;
    }

    private PublicDataApiItemDto toRailItem(
            JsonNode rawItemNode,
            KricStationCodeLoader.StationReference stationReference
    ) {
        ObjectNode normalizedItem;
        if (rawItemNode.isObject()) {
            normalizedItem = ((ObjectNode) rawItemNode).deepCopy();
        } else {
            normalizedItem = objectMapper.createObjectNode();
            normalizedItem.set("value", rawItemNode);
        }

        putIfBlank(normalizedItem, "lnCd", stationReference.lnCd());
        putIfBlank(normalizedItem, "railOprIsttCd", stationReference.railOprIsttCd());
        putIfBlank(normalizedItem, "stinCd", stationReference.stinCd());
        // 역사 코드 파일의 표준 컬럼명을 그대로 저장해 후처리 조인 없이 조회할 수 있게 한다.
        putIfBlank(normalizedItem, "LN_NM", stationReference.lnNm());
        putIfBlank(normalizedItem, "STIN_NM", stationReference.stinNm());

        try {
            String payloadJson = objectMapper.writeValueAsString(normalizedItem);
            String payloadHash = sha256(payloadJson);
            String externalId = buildRailExternalId(normalizedItem);
            return new PublicDataApiItemDto(externalId, payloadJson, payloadHash);
        } catch (JsonProcessingException exception) {
            throw new PayloadParseException("역사별 휠체어리프트 응답 직렬화 실패", exception);
        }
    }

    private void putIfBlank(ObjectNode node, String fieldName, String fallbackValue) {
        String currentValue = node.path(fieldName).asText("").trim();
        if (!currentValue.isBlank()) {
            return;
        }
        node.put(fieldName, fallbackValue == null ? "" : fallbackValue.trim());
    }

    private boolean containsRailField(JsonNode node) {
        return node.has("railOprIsttCd")
                || node.has("lnCd")
                || node.has("stinCd")
                || node.has("exitNo")
                || node.has("dtlLoc");
    }

    private String buildRailExternalId(ObjectNode normalizedItem) {
        String identity = String.join("|",
                normalizedItem.path("railOprIsttCd").asText("").trim(),
                normalizedItem.path("lnCd").asText("").trim(),
                normalizedItem.path("stinCd").asText("").trim(),
                normalizedItem.path("exitNo").asText("").trim(),
                normalizedItem.path("runStinFlorFr").asText("").trim(),
                normalizedItem.path("runStinFlorTo").asText("").trim(),
                normalizedItem.path("dtlLoc").asText("").trim(),
                normalizedItem.path("grndDvNmFr").asText("").trim(),
                normalizedItem.path("grndDvNmTo").asText("").trim()
        );
        return "rail-" + sha256(identity);
    }

    private List<PublicDataApiItemDto> mapItems(JsonNode itemsNode, BridgeWorkSyncProperties.SourceConfig sourceConfig) {
        List<PublicDataApiItemDto> items = new ArrayList<>();

        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                items.add(toItem(itemNode, sourceConfig));
            }
            return items;
        }

        if (itemsNode.isObject()) {
            items.add(toItem(itemsNode, sourceConfig));
        }

        return items;
    }

    private PublicDataApiItemDto toItem(JsonNode itemNode, BridgeWorkSyncProperties.SourceConfig sourceConfig) {
        try {
            String payloadJson = objectMapper.writeValueAsString(itemNode);
            String payloadHash = sha256(payloadJson);
            String externalId = extractExternalId(itemNode, sourceConfig.getItemIdField()).orElse(payloadHash);
            return new PublicDataApiItemDto(externalId, payloadJson, payloadHash);
        } catch (JsonProcessingException exception) {
            throw new PayloadParseException("공공데이터 응답 직렬화 실패", exception);
        }
    }

    private Optional<String> extractExternalId(JsonNode itemNode, String itemIdField) {
        if (itemIdField == null || itemIdField.isBlank()) {
            return Optional.empty();
        }

        JsonNode selectedNode = itemNode;
        for (String path : itemIdField.split("\\.")) {
            if (selectedNode == null || selectedNode.isMissingNode()) {
                return Optional.empty();
            }
            selectedNode = selectedNode.path(path);
        }

        if (selectedNode == null || selectedNode.isMissingNode() || selectedNode.isNull()) {
            return Optional.empty();
        }

        String externalId = selectedNode.asText("").trim();
        return externalId.isEmpty() ? Optional.empty() : Optional.of(externalId);
    }

    private JsonNode parseXml(String responseBody, String errorMessage) {
        try {
            return xmlMapper.readTree(responseBody.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new PayloadParseException(errorMessage, exception);
        }
    }

    private void validateApiResultOrThrow(JsonNode rootNode, PublicDataSourceType sourceType) {
        String resultCode = extractTextAt(rootNode, "/response/header/resultCode")
                .or(() -> extractTextAt(rootNode, "/header/resultCode"))
                .orElse("");

        if (resultCode.isBlank() || isSuccessResultCode(resultCode) || isNoDataResultCode(sourceType, resultCode)) {
            return;
        }

        String resultMessage = extractTextAt(rootNode, "/response/header/resultMsg")
                .or(() -> extractTextAt(rootNode, "/header/resultMsg"))
                .orElse("알 수 없는 오류");

        throw new ExternalApiException(
                "공공데이터 API 응답 오류(" + sourceType + "): "
                        + resultMessage
                        + " [resultCode=" + resultCode + "]"
        );
    }

    private boolean isSuccessResultCode(String resultCode) {
        String normalized = resultCode.trim();
        return "00".equals(normalized) || "0000".equals(normalized) || "0".equals(normalized);
    }

    private boolean isNoDataResultCode(PublicDataSourceType sourceType, String resultCode) {
        if (sourceType != PublicDataSourceType.RAIL_WHEELCHAIR_LIFT) {
            return false;
        }
        return "03".equals(resultCode.trim());
    }

    private Optional<String> extractTextAt(JsonNode rootNode, String pointer) {
        JsonNode node = rootNode.at(pointer);
        if (node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }

        String value = node.asText("").trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private boolean hasNextPage(int totalCount, int pageNo, int pageSize, int itemSize) {
        if (totalCount >= 0) {
            return pageNo * pageSize < totalCount;
        }

        return itemSize >= pageSize;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 원문 기반 해시를 고정 키로 사용해 API id 누락 시에도 중복 저장을 방지한다.
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
