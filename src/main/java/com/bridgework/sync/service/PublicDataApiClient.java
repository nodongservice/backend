package com.bridgework.sync.service;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.dto.PublicDataApiItemDto;
import com.bridgework.sync.dto.PublicDataApiPageResponseDto;
import com.bridgework.sync.dto.SourceLatestRevisionDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.exception.ExternalApiException;
import com.bridgework.sync.exception.PayloadParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
    private static final Pattern ODCLOUD_ENDPOINT_PATTERN =
            Pattern.compile("(?:https?://|//)?(?:api\\.odcloud\\.kr)?/?api/([0-9]{5,})/v1/([a-zA-Z0-9:%_:-]{5,})",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ODCLOUD_UDDI_DETAIL_PATTERN =
            Pattern.compile("uddi(?::|%3A)[0-9a-fA-F-]{20,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_TOKEN_PATTERN =
            Pattern.compile("(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern DATE_TOKEN_COMPACT_PATTERN =
            Pattern.compile("(?<!\\d)(20\\d{2})(\\d{2})(\\d{2})(?!\\d)");
    private static final Pattern SWAGGER_OAS_URL_PATTERN =
            Pattern.compile("url\\s*:\\s*['\"]([^'\"]*oas/docs\\?namespace=[^'\"]+)['\"]",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ODCLOUD_OAS_PATH_PATTERN =
            Pattern.compile("^/?([0-9]{5,})/v1/([^/?#]+)$");
    private static final Pattern CREDENTIAL_QUERY_PARAM_PATTERN =
            Pattern.compile("([?&](?:serviceKey|authKey)=)([^&]+)");
    private static final LocalDate UNKNOWN_MODIFIED_DATE = LocalDate.of(1970, 1, 1);
    private static final DateTimeFormatter SEOUL_FILE_MODIFIED_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String SEOUL_FILE_DOWNLOAD_URL = "https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do?useCache=false";
    private static final Set<String> RETRYABLE_RESULT_CODES = Set.of(
            "01", "02", "04", "05", "21", "22",
            "ERROR-500", "ERROR-600", "ERROR-601"
    );
    private static final Set<String> PERMANENT_RESULT_CODES = Set.of(
            "10", "11", "12", "20", "30", "31", "32", "33",
            "INFO-100",
            "ERROR-300", "ERROR-301", "ERROR-310",
            "ERROR-331", "ERROR-332", "ERROR-333", "ERROR-334", "ERROR-335", "ERROR-336"
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;
    private final BridgeWorkSyncProperties syncProperties;
    private final KricStationCodeLoader kricStationCodeLoader;
    private final DataFormatter excelDataFormatter = new DataFormatter(Locale.KOREA);

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
        if (sourceType == PublicDataSourceType.NATIONWIDE_BUS_STOP
        ) {
            return fetchDataGoFileDataPage(sourceConfig, pageNo);
        }
        if (isSeoulDatasetFileSource(sourceType)) {
            return fetchSeoulDatasetFilePage(sourceConfig, pageNo);
        }
        if (sourceType == PublicDataSourceType.RAIL_WHEELCHAIR_LIFT
                || sourceType == PublicDataSourceType.RAIL_WHEELCHAIR_LIFT_MOVEMENT) {
            return fetchRailWheelchairLiftPage(sourceConfig, pageNo);
        }
        if (sourceType == PublicDataSourceType.SEOUL_SUBWAY_ENTRANCE_LIFT
                || sourceType == PublicDataSourceType.SEOUL_WALKING_NETWORK) {
            return fetchSeoulOpenApiPage(sourceConfig, pageNo);
        }
        if (sourceType == PublicDataSourceType.NATIONWIDE_TRAFFIC_LIGHT
                || sourceType == PublicDataSourceType.NATIONWIDE_CROSSWALK) {
            return fetchDataGoXmlPage(sourceConfig, pageNo);
        }
        if (sourceType == PublicDataSourceType.VOCATIONAL_TRAINING) {
            return fetchVocationalTrainingPage(sourceConfig, pageNo);
        }
        if (sourceType == PublicDataSourceType.JOBSEEKER_COMPETENCY_PROGRAM) {
            return fetchJobseekerCompetencyProgramPage(sourceConfig, pageNo);
        }

        String requestUri = buildRequestUri(sourceConfig, pageNo);
        String responseBody = fetchBody(requestUri, sourceConfig);

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

    public Optional<SourceLatestRevisionDto> fetchLatestRevision(BridgeWorkSyncProperties.SourceConfig sourceConfig) {
        if (isSeoulDatasetFileSource(sourceConfig.getSourceType())) {
            SeoulDatasetLatestFile latestFile = resolveSeoulDatasetLatestFile(sourceConfig);
            return Optional.of(new SourceLatestRevisionDto(
                    latestFile.revisionKey(),
                    latestFile.fileName(),
                    latestFile.modifiedDate().toString()
            ));
        }

        if (isDataGoFileDataSource(sourceConfig.getSourceType())) {
            DataGoFileDataVersion latestVersion = resolveLatestDataGoFileDataVersion(sourceConfig);
            return Optional.of(new SourceLatestRevisionDto(
                    latestVersion.revisionKey(),
                    latestVersion.displayName(),
                    latestVersion.modifiedDate().toString()
            ));
        }

        return Optional.empty();
    }

    private PublicDataApiPageResponseDto fetchSeoulWheelchairLiftPage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        if (pageNo > 1) {
            return new PublicDataApiPageResponseDto(List.of(), false);
        }

        DataGoFileDataVersion latestVersion = resolveLatestDataGoFileDataVersion(sourceConfig);
        String publicDataPk = latestVersion.publicDataPk();
        String publicDataDetailPk = latestVersion.publicDataDetailPk();

        List<String> stationNames = kricStationCodeLoader.loadSeoulStationNames();
        List<PublicDataApiItemDto> aggregatedItems = new ArrayList<>();
        Set<String> dedupedExternalIds = new LinkedHashSet<>();
        boolean shouldFallbackToAll = false;

        for (String stationName : stationNames) {
            List<PublicDataApiItemDto> stationItems;
            try {
                stationItems = fetchAllDataGoFileDataItems(
                        sourceConfig,
                        publicDataPk,
                        publicDataDetailPk,
                        "역명",
                        stationName,
                        "station=" + stationName
                );
            } catch (ExternalApiException exception) {
                if (isDataGoFileDataFilterRejected(exception)) {
                    // 일부 fileData 버전은 한글 컬럼 필터를 허용하지 않으므로 전체 조회로 전환한다.
                    log.warn("[FILEDATA] source={} filterField=역명 filterValue={} rejectedByApi, fallback=all",
                            sourceConfig.getSourceType(),
                            stationName);
                    shouldFallbackToAll = true;
                    break;
                }
                throw exception;
            }

            for (PublicDataApiItemDto stationItem : stationItems) {
                if (dedupedExternalIds.add(stationItem.externalId())) {
                    aggregatedItems.add(stationItem);
                }
            }
        }

        if (!shouldFallbackToAll && !aggregatedItems.isEmpty()) {
            return new PublicDataApiPageResponseDto(aggregatedItems, false);
        }

        // 역사명 필터가 불일치하는 경우를 대비해 전체 조회를 수행한다.
        List<PublicDataApiItemDto> fallbackItems = fetchAllDataGoFileDataItems(
                sourceConfig,
                publicDataPk,
                publicDataDetailPk,
                null,
                null,
                "fallback=all"
        );
        return new PublicDataApiPageResponseDto(fallbackItems, false);
    }

    private boolean isDataGoFileDataFilterRejected(ExternalApiException exception) {
        if (exception == null || exception.getMessage() == null) {
            return false;
        }
        String message = exception.getMessage();
        return message.contains("호출 실패")
                && message.contains(": 400");
    }

    private PublicDataApiPageResponseDto fetchDataGoFileDataPage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        if (pageNo > 1) {
            return new PublicDataApiPageResponseDto(List.of(), false);
        }

        DataGoFileDataVersion latestVersion = resolveLatestDataGoFileDataVersion(sourceConfig);
        String publicDataPk = latestVersion.publicDataPk();
        String publicDataDetailPk = latestVersion.publicDataDetailPk();

        List<PublicDataApiItemDto> items = fetchAllDataGoFileDataItems(
                sourceConfig,
                publicDataPk,
                publicDataDetailPk,
                null,
                null,
                "all"
        );
        return new PublicDataApiPageResponseDto(items, false);
    }

    private DataGoFileDataVersion resolveLatestDataGoFileDataVersion(BridgeWorkSyncProperties.SourceConfig sourceConfig) {
        String fileDataPageBody = fetchBody(sourceConfig.getBaseUrl(), sourceConfig);
        Document document = Jsoup.parse(fileDataPageBody);
        String fallbackPublicDataPk = extractFileDataField(document, "publicDataPk");

        List<DataGoFileDataCandidate> candidates = new ArrayList<>(resolveDataGoFileDataCandidates(
                fileDataPageBody,
                sourceConfig,
                fallbackPublicDataPk,
                sourceConfig.getSourceType()
        ).stream()
                // 상세키는 동일 publicDataPk 범위에서만 신뢰한다.
                .filter(candidate -> fallbackPublicDataPk.equals(candidate.publicDataPk()))
                .toList());
        if (candidates.isEmpty()) {
            throw new ExternalApiException(
                    "파일데이터 최신 OpenAPI 후보 추출 실패: source="
                            + sourceConfig.getSourceType()
                            + ", baseUrl="
                            + sourceConfig.getBaseUrl()
            );
        }
        DataGoFileDataCandidate selected = candidates.stream()
                .max(Comparator
                        // 최신 fileData는 uddi 식별자를 사용하므로 숫자형보다 우선 선택한다.
                        .comparing((DataGoFileDataCandidate candidate) -> isUddiDetailPk(candidate.publicDataDetailPk()))
                        .thenComparing((DataGoFileDataCandidate candidate) -> candidate.modifiedDate() != null)
                        .thenComparing(candidate -> Optional.ofNullable(candidate.modifiedDate()).orElse(UNKNOWN_MODIFIED_DATE))
                        .thenComparingInt(candidate -> parseIntSafe(candidate.publicDataDetailPk())))
                .orElseThrow(() -> new ExternalApiException(
                        "파일데이터 최신 OpenAPI 후보 선택 실패: source=" + sourceConfig.getSourceType()
                ));

        LocalDate modifiedDate = Optional.ofNullable(selected.modifiedDate()).orElse(UNKNOWN_MODIFIED_DATE);
        String revisionDate = selected.modifiedDate() == null ? "unknown" : modifiedDate.toString();
        String displayName = "publicDataDetailPk=" + selected.publicDataDetailPk();
        // fileData 기반 최신성 판정은 swagger 제목/수정일(날짜) 중심으로 한다.
        // detailPk 교체가 있어도 날짜가 동일하면 재수집을 스킵할 수 있도록 revisionKey를 날짜+dataset으로 구성한다.
        String revisionKey = revisionDate + "|" + selected.publicDataPk();

        log.info("[FILEDATA] source={} selected publicDataPk={} publicDataDetailPk={} modifiedDate={} matchedFrom={}",
                sourceConfig.getSourceType(),
                selected.publicDataPk(),
                selected.publicDataDetailPk(),
                selected.modifiedDate(),
                selected.contextLabel());

        return new DataGoFileDataVersion(
                selected.publicDataPk(),
                selected.publicDataDetailPk(),
                revisionKey,
                displayName,
                modifiedDate
        );
    }

    private List<DataGoFileDataCandidate> resolveDataGoFileDataCandidates(String htmlBody,
                                                                          BridgeWorkSyncProperties.SourceConfig sourceConfig,
                                                                          String fallbackPublicDataPk,
                                                                          PublicDataSourceType sourceType) {
        List<DataGoFileDataCandidate> candidates = new ArrayList<>();

        // 1) fileData 페이지 내 SwaggerUI OAS URL을 우선 사용해 최신 후보를 추출한다.
        collectDataGoCandidatesFromSwaggerOas(
                htmlBody,
                sourceConfig,
                fallbackPublicDataPk,
                sourceType,
                candidates
        );

        // 2) OAS에서 후보를 찾지 못한 경우에만 openapi 페이지 본문 파싱으로 보조 탐색한다.
        if (candidates.isEmpty()) {
            OpenApiPage openApiPage = fetchOpenApiPage(sourceConfig);
            if (openApiPage != null && openApiPage.body() != null && !openApiPage.body().isBlank()) {
                collectDataGoCandidatesFromSwaggerPage(
                        openApiPage.url(),
                        openApiPage.body(),
                        sourceConfig,
                        fallbackPublicDataPk,
                        candidates
                );
            }
        }

        Map<String, DataGoFileDataCandidate> dedupedCandidates = new LinkedHashMap<>();
        for (DataGoFileDataCandidate candidate : candidates) {
            String key = candidate.publicDataPk()
                    + "|"
                    + candidate.publicDataDetailPk()
                    + "|"
                    + (candidate.modifiedDate() == null ? "" : candidate.modifiedDate().toString());
            dedupedCandidates.putIfAbsent(key, candidate);
        }

        if (dedupedCandidates.isEmpty()) {
            log.warn("[FILEDATA] source={} swagger 영역에서 최신 API 후보를 찾지 못했습니다.", sourceType);
        }
        return new ArrayList<>(dedupedCandidates.values());
    }

    private void collectDataGoCandidatesFromSwaggerOas(String fileDataPageBody,
                                                       BridgeWorkSyncProperties.SourceConfig sourceConfig,
                                                       String fallbackPublicDataPk,
                                                       PublicDataSourceType sourceType,
                                                       List<DataGoFileDataCandidate> candidates) {
        String swaggerOasUrl = extractSwaggerOasUrl(fileDataPageBody);
        if (swaggerOasUrl == null || swaggerOasUrl.isBlank()) {
            log.warn("[FILEDATA] source={} fileData 페이지에서 swagger OAS URL을 찾지 못했습니다.", sourceType);
            return;
        }

        String oasBody;
        try {
            oasBody = fetchBody(swaggerOasUrl, sourceConfig);
        } catch (Exception exception) {
            log.warn("[FILEDATA] source={} swagger OAS 조회 실패 url={} reason={}",
                    sourceType,
                    swaggerOasUrl,
                    exception.getMessage());
            return;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(oasBody);
            JsonNode pathsNode = rootNode.path("paths");
            if (!pathsNode.isObject()) {
                log.warn("[FILEDATA] source={} swagger OAS paths가 비어 있습니다. url={}", sourceType, swaggerOasUrl);
                return;
            }

            Iterator<Map.Entry<String, JsonNode>> iterator = pathsNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                String pathKey = entry.getKey();
                Matcher pathMatcher = ODCLOUD_OAS_PATH_PATTERN.matcher(pathKey == null ? "" : pathKey.trim());
                if (!pathMatcher.find()) {
                    continue;
                }

                String publicDataPk = pathMatcher.group(1).trim();
                String publicDataDetailPk = normalizeDataGoDetailPk(pathMatcher.group(2));
                if (publicDataDetailPk.isBlank()) {
                    continue;
                }
                if (fallbackPublicDataPk != null
                        && !fallbackPublicDataPk.isBlank()
                        && !fallbackPublicDataPk.equals(publicDataPk)) {
                    continue;
                }

                JsonNode getNode = entry.getValue() == null ? null : entry.getValue().path("get");
                String summary = getNode == null ? "" : getNode.path("summary").asText("");
                String description = getNode == null ? "" : getNode.path("description").asText("");
                LocalDate modifiedDate = extractFirstDate(summary)
                        .or(() -> extractFirstDate(description))
                        .orElse(null);

                candidates.add(new DataGoFileDataCandidate(
                        publicDataPk,
                        publicDataDetailPk,
                        modifiedDate,
                        "swagger-oas-path"
                ));
            }
        } catch (Exception exception) {
            log.warn("[FILEDATA] source={} swagger OAS 파싱 실패 url={} reason={}",
                    sourceType,
                    swaggerOasUrl,
                    exception.getMessage());
        }
    }

    private String extractSwaggerOasUrl(String fileDataPageBody) {
        if (fileDataPageBody == null || fileDataPageBody.isBlank()) {
            return null;
        }

        String normalizedBody = normalizeSwaggerCandidateText(fileDataPageBody);
        Matcher matcher = SWAGGER_OAS_URL_PATTERN.matcher(normalizedBody);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        matcher = SWAGGER_OAS_URL_PATTERN.matcher(fileDataPageBody);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private void collectDataGoCandidatesFromSwaggerPage(String openApiPageUrl,
                                                        String openApiPageBody,
                                                        BridgeWorkSyncProperties.SourceConfig sourceConfig,
                                                        String fallbackPublicDataPk,
                                                        List<DataGoFileDataCandidate> candidates) {
        Document openApiDocument = Jsoup.parse(openApiPageBody);

        collectDataGoCandidatesFromText(
                openApiPageBody,
                extractFirstDate(openApiDocument.text()).orElse(null),
                "openapi-page-body",
                fallbackPublicDataPk,
                candidates
        );
        collectDataGoCandidatesFromText(
                normalizeSwaggerCandidateText(openApiPageBody),
                extractFirstDate(openApiDocument.text()).orElse(null),
                "openapi-page-body-normalized",
                fallbackPublicDataPk,
                candidates
        );

        for (Element swaggerElement : openApiDocument.select("#swagger-container, #swagger-ui, [id*=swagger]")) {
            LocalDate modifiedDate = extractFirstDate(swaggerElement.text()).orElse(null);
            collectDataGoCandidatesFromText(
                    swaggerElement.outerHtml(),
                    modifiedDate,
                    "swagger-container",
                    fallbackPublicDataPk,
                    candidates
            );
            collectDataGoCandidatesFromText(
                    normalizeSwaggerCandidateText(swaggerElement.outerHtml()),
                    modifiedDate,
                    "swagger-container-normalized",
                    fallbackPublicDataPk,
                    candidates
            );
        }

        for (Element scriptElement : openApiDocument.select("script")) {
            String scriptData = scriptElement.data();
            if (scriptData == null || scriptData.isBlank()) {
                continue;
            }
            String normalizedScriptData = normalizeSwaggerCandidateText(scriptData);
            LocalDate modifiedDate = extractFirstDate(normalizedScriptData)
                    .or(() -> extractFirstDate(scriptData))
                    .orElse(null);

            collectDataGoCandidatesFromText(
                    scriptData,
                    modifiedDate,
                    "swagger-script",
                    fallbackPublicDataPk,
                    candidates
            );
            collectDataGoCandidatesFromText(
                    normalizedScriptData,
                    modifiedDate,
                    "swagger-script-normalized",
                    fallbackPublicDataPk,
                    candidates
            );
        }

        // openapi 페이지의 외부 script는 공통 UI 스크립트가 대부분이라 불필요한 네트워크 호출을 줄이기 위해 탐색하지 않는다.
    }

    private OpenApiPage fetchOpenApiPage(BridgeWorkSyncProperties.SourceConfig sourceConfig) {
        String baseUrl = sourceConfig.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }

        String openApiPageUrl = baseUrl.replaceFirst("(?i)/filedata\\.do$", "/openapi.do");
        if (openApiPageUrl.equals(baseUrl)) {
            openApiPageUrl = baseUrl.replaceFirst("(?i)/filedata$", "/openapi.do");
        }
        if (openApiPageUrl.equals(baseUrl)) {
            return null;
        }

        try {
            return new OpenApiPage(openApiPageUrl, fetchBody(openApiPageUrl, sourceConfig));
        } catch (Exception exception) {
            log.warn("[FILEDATA] source={} openapi 페이지 조회 실패 url={} reason={}",
                    sourceConfig.getSourceType(),
                    openApiPageUrl,
                    exception.getMessage());
            return null;
        }
    }


    private String normalizeSwaggerCandidateText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String normalized = Entities.unescape(rawText);
        normalized = normalized
                .replace("\\/", "/")
                .replace("\\u002F", "/")
                .replace("\\u002f", "/")
                .replace("\\u003A", ":")
                .replace("\\u003a", ":")
                .replace("\\u0026", "&");

        String decoded = decodePercentEscapesSafely(normalized);
        if (decoded != null && !decoded.isBlank()) {
            return decoded;
        }
        return normalized;
    }

    private void collectDataGoCandidatesFromText(String text,
                                                 LocalDate modifiedDate,
                                                 String contextLabel,
                                                 String fallbackPublicDataPk,
                                                 List<DataGoFileDataCandidate> candidates) {
        Matcher endpointMatcher = ODCLOUD_ENDPOINT_PATTERN.matcher(text);
        while (endpointMatcher.find()) {
            String publicDataPk = endpointMatcher.group(1).trim();
            String publicDataDetailPk = normalizeDataGoDetailPk(endpointMatcher.group(2));
            if (publicDataDetailPk.isBlank()) {
                continue;
            }
            candidates.add(new DataGoFileDataCandidate(publicDataPk, publicDataDetailPk, modifiedDate, contextLabel));
        }

        if (fallbackPublicDataPk == null || fallbackPublicDataPk.isBlank()) {
            return;
        }

        Matcher uddiMatcher = ODCLOUD_UDDI_DETAIL_PATTERN.matcher(text);
        while (uddiMatcher.find()) {
            String publicDataDetailPk = normalizeDataGoDetailPk(uddiMatcher.group());
            if (!isUddiDetailPk(publicDataDetailPk)) {
                continue;
            }
            candidates.add(new DataGoFileDataCandidate(
                    fallbackPublicDataPk.trim(),
                    publicDataDetailPk,
                    modifiedDate,
                    contextLabel + "-uddi-token"
            ));
        }
    }

    private String normalizeDataGoDetailPk(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }

        String normalized = rawValue.trim();
        normalized = normalized.replace("%3A", ":").replace("%3a", ":");
        normalized = decodePercentEscapesSafely(normalized);
        normalized = normalized.replaceAll("[\"'<>\\s]+$", "");
        return normalized;
    }

    private List<String> extractRegexMatches(String text, Pattern pattern) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isBlank()) {
                matches.add(value);
            }
        }
        return matches;
    }

    private Optional<LocalDate> extractFirstDate(String text) {
        Matcher compactMatcher = DATE_TOKEN_COMPACT_PATTERN.matcher(text);
        while (compactMatcher.find()) {
            try {
                int year = Integer.parseInt(compactMatcher.group(1));
                int month = Integer.parseInt(compactMatcher.group(2));
                int day = Integer.parseInt(compactMatcher.group(3));
                return Optional.of(LocalDate.of(year, month, day));
            } catch (RuntimeException exception) {
                // 잘못된 날짜 토큰은 무시하고 다음 후보를 계속 탐색한다.
            }
        }

        Matcher matcher = DATE_TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return Optional.of(LocalDate.of(year, month, day));
            } catch (RuntimeException exception) {
                // 잘못된 날짜 토큰은 무시하고 다음 후보를 계속 탐색한다.
            }
        }
        return Optional.empty();
    }

    private PublicDataApiPageResponseDto fetchSeoulDatasetFilePage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        if (pageNo > 1) {
            return new PublicDataApiPageResponseDto(List.of(), false);
        }

        SeoulDatasetLatestFile latestFile = resolveSeoulDatasetLatestFile(sourceConfig);
        byte[] fileBytes = downloadSeoulDatasetFile(sourceConfig.getSourceType(), latestFile);
        List<Map<String, String>> rows = parseSeoulDatasetRows(sourceConfig.getSourceType(), latestFile.fileName(), fileBytes);
        List<PublicDataApiItemDto> items = toItemDtos(rows, sourceConfig);

        log.info("[COUNT] source={} fileName={} modifiedDate={} detected={}",
                sourceConfig.getSourceType(),
                latestFile.fileName(),
                latestFile.modifiedDateText(),
                items.size());
        return new PublicDataApiPageResponseDto(items, false);
    }

    private boolean isSeoulDatasetFileSource(PublicDataSourceType sourceType) {
        return sourceType == PublicDataSourceType.SEOUL_WHEELCHAIR_RAMP_STATUS
                || sourceType == PublicDataSourceType.SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION;
    }

    private boolean isDataGoFileDataSource(PublicDataSourceType sourceType) {
        return sourceType == PublicDataSourceType.SEOUL_WHEELCHAIR_LIFT
                || sourceType == PublicDataSourceType.NATIONWIDE_BUS_STOP;
    }

    private SeoulDatasetLatestFile resolveSeoulDatasetLatestFile(BridgeWorkSyncProperties.SourceConfig sourceConfig) {
        String htmlBody = fetchBody(sourceConfig.getBaseUrl(), sourceConfig);

        Document document = Jsoup.parse(htmlBody);
        Element frmFile = document.selectFirst("form[name=frmFile]");

        String infId = resolveFormInputValue(document, frmFile, "infId", sourceConfig.getSourceType());
        String infSeq = resolveFormInputValue(document, frmFile, "infSeq", sourceConfig.getSourceType());

        Elements rows = document.select("tr[id^=fileTr_]");
        if (rows.isEmpty()) {
            rows = document.select("tr:has(span[onclick*=downloadFile])");
        }
        if (rows.isEmpty()) {
            throw new ExternalApiException("서울 파일데이터 페이지에 파일 목록이 없습니다: " + sourceConfig.getSourceType());
        }

        List<SeoulDatasetLatestFile> candidates = new ArrayList<>();
        for (Element rowElement : rows) {
            Element clickableSpan = rowElement.selectFirst("span[onclick*=downloadFile]");
            if (clickableSpan == null) {
                continue;
            }

            String onclick = clickableSpan.attr("onclick");
            Matcher seqMatcher = Pattern.compile("downloadFile\\('([0-9]+)'\\)").matcher(onclick);
            if (!seqMatcher.find()) {
                continue;
            }

            String seq = seqMatcher.group(1).trim();
            String fileName = clickableSpan.attr("title").trim();
            if (fileName.isBlank()) {
                fileName = clickableSpan.text().trim();
            }

            Elements columns = rowElement.select("td");
            if (columns.size() < 5) {
                continue;
            }

            String modifiedDateText = columns.get(4).text().trim();
            LocalDate modifiedDate = parseSeoulModifiedDate(modifiedDateText);
            String revisionKey = modifiedDate + "|" + seq + "|" + fileName;

            candidates.add(new SeoulDatasetLatestFile(
                    infId,
                    infSeq,
                    seq,
                    fileName,
                    modifiedDateText,
                    modifiedDate,
                    revisionKey
            ));
        }

        return candidates.stream()
                .max((left, right) -> {
                    int dateCompare = left.modifiedDate().compareTo(right.modifiedDate());
                    if (dateCompare != 0) {
                        return dateCompare;
                    }
                    return Integer.compare(parseIntSafe(left.seq()), parseIntSafe(right.seq()));
                })
                .orElseThrow(() -> new ExternalApiException("서울 파일데이터 최신 파일을 찾을 수 없습니다: " + sourceConfig.getSourceType()));
    }

    private String resolveFormInputValue(Document document,
                                         Element formElement,
                                         String inputName,
                                         PublicDataSourceType sourceType) {
        Element inputElement = null;
        if (formElement != null) {
            inputElement = formElement.selectFirst("input[name=" + inputName + "]");
        }
        if (inputElement == null) {
            // 일부 서울 파일데이터 페이지는 frmFile name 없이 hidden input만 제공한다.
            inputElement = document.selectFirst("input[name=" + inputName + "]");
        }
        if (inputElement == null) {
            throw new ExternalApiException("서울 파일데이터 폼 파라미터 누락(" + inputName + "): " + sourceType);
        }
        String value = inputElement.attr("value").trim();
        if (value.isBlank()) {
            throw new ExternalApiException("서울 파일데이터 폼 파라미터가 비어 있습니다(" + inputName + "): " + sourceType);
        }
        return value;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private boolean isUddiDetailPk(String detailPk) {
        return detailPk != null && detailPk.toLowerCase(Locale.ROOT).startsWith("uddi:");
    }

    private LocalDate parseSeoulModifiedDate(String modifiedDateText) {
        String normalized = modifiedDateText.replaceAll("\\s+", "").replaceAll("\\.$", "");
        if (!normalized.matches("\\d{4}\\.\\d{2}\\.\\d{2}")) {
            throw new ExternalApiException("서울 파일데이터 수정일 형식이 올바르지 않습니다: " + modifiedDateText);
        }

        return LocalDate.parse(normalized, SEOUL_FILE_MODIFIED_DATE_FORMATTER);
    }

    private byte[] downloadSeoulDatasetFile(PublicDataSourceType sourceType, SeoulDatasetLatestFile latestFile) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("infId", latestFile.infId());
        formData.add("seqNo", latestFile.seq());
        formData.add("seq", latestFile.seq());
        formData.add("infSeq", latestFile.infSeq());

        log.info("[HTTP] source={} uri={} params=infId={},infSeq={},seq={}",
                sourceType,
                SEOUL_FILE_DOWNLOAD_URL,
                latestFile.infId(),
                latestFile.infSeq(),
                latestFile.seq());

        return webClient
                .post()
                .uri(SEOUL_FILE_DOWNLOAD_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new ExternalApiException(
                                        "서울 파일데이터 다운로드 실패(" + sourceType + "): "
                                                + clientResponse.statusCode().value()
                                ))
                )
                .bodyToMono(byte[].class)
                .timeout(syncProperties.getRequestTimeout())
                .retryWhen(
                        Retry.backoff(API_RETRY_COUNT, API_RETRY_BACKOFF)
                                .filter(this::isRetryableApiException)
                )
                .blockOptional()
                .orElseThrow(() -> new ExternalApiException("서울 파일데이터 다운로드 응답이 비어 있습니다: " + sourceType));
    }

    private List<Map<String, String>> parseSeoulDatasetRows(
            PublicDataSourceType sourceType,
            String fileName,
            byte[] fileBytes
    ) {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (lowerFileName.endsWith(".xlsx")) {
            return parseXlsxRows(sourceType, fileBytes);
        }
        if (lowerFileName.endsWith(".csv")) {
            return parseCsvRows(sourceType, fileBytes);
        }
        throw new PayloadParseException(
                "서울 파일데이터 확장자를 지원하지 않습니다: " + fileName,
                new IllegalArgumentException(fileName)
        );
    }

    private List<Map<String, String>> parseXlsxRows(PublicDataSourceType sourceType, byte[] fileBytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return List.of();
            }

            List<String> headers = extractHeaders(headerRow);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                Map<String, String> rowMap = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                    Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : excelDataFormatter.formatCellValue(cell).trim();
                    if (!value.isBlank()) {
                        hasValue = true;
                    }
                    rowMap.put(headers.get(columnIndex), value);
                }
                if (hasValue) {
                    rows.add(rowMap);
                }
            }
            return rows;
        } catch (Exception exception) {
            throw new PayloadParseException("서울 파일데이터 xlsx 파싱 실패: " + sourceType, exception);
        }
    }

    private List<String> extractHeaders(Row headerRow) {
        short lastCellNum = headerRow.getLastCellNum();
        if (lastCellNum <= 0) {
            return List.of();
        }

        List<String> headers = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
            Cell cell = headerRow.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String header = cell == null ? "" : excelDataFormatter.formatCellValue(cell).trim();
            if (header.isBlank()) {
                header = "COLUMN_" + (columnIndex + 1);
            }
            headers.add(header);
        }
        return headers;
    }

    private List<Map<String, String>> parseCsvRows(PublicDataSourceType sourceType, byte[] fileBytes) {
        String csvText = decodeCsvBytes(fileBytes);
        try (CSVParser parser = CSVParser.parse(
                new StringReader(csvText),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
        )) {
            Map<String, Integer> headerMap = parser.getHeaderMap();
            if (headerMap == null || headerMap.isEmpty()) {
                return List.of();
            }

            List<String> headers = headerMap.keySet().stream().toList();
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                boolean hasValue = false;
                for (String header : headers) {
                    String value = record.isMapped(header) ? record.get(header).trim() : "";
                    if (!value.isBlank()) {
                        hasValue = true;
                    }
                    rowMap.put(header, value);
                }
                if (hasValue) {
                    rows.add(rowMap);
                }
            }
            return rows;
        } catch (Exception exception) {
            throw new PayloadParseException("서울 파일데이터 csv 파싱 실패: " + sourceType, exception);
        }
    }

    private String decodeCsvBytes(byte[] fileBytes) {
        String utf8Text = new String(fileBytes, StandardCharsets.UTF_8);
        if (looksLikeBrokenUtf8(utf8Text)) {
            String cp949Text = new String(fileBytes, java.nio.charset.Charset.forName("MS949"));
            if (!looksLikeBrokenUtf8(cp949Text)) {
                return cp949Text;
            }
        }
        return removeUtf8Bom(utf8Text);
    }

    private boolean looksLikeBrokenUtf8(String text) {
        return text.contains("�");
    }

    private String removeUtf8Bom(String text) {
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    private List<PublicDataApiItemDto> toItemDtos(
            List<Map<String, String>> rows,
            BridgeWorkSyncProperties.SourceConfig sourceConfig
    ) {
        List<PublicDataApiItemDto> items = new ArrayList<>();
        for (Map<String, String> row : rows) {
            try {
                JsonNode node = objectMapper.valueToTree(row);
                items.add(toItem(node, sourceConfig));
            } catch (Exception exception) {
                throw new PayloadParseException("서울 파일데이터 행 변환 실패: " + sourceConfig.getSourceType(), exception);
            }
        }
        return items;
    }

    private List<PublicDataApiItemDto> fetchAllDataGoFileDataItems(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            String publicDataPk,
            String publicDataDetailPk,
            String filterField,
            String filterValue,
            String contextLabel
    ) {
        List<PublicDataApiItemDto> records = new ArrayList<>();

        for (int fileDataPageNo = 1; fileDataPageNo <= sourceConfig.getMaxPages(); fileDataPageNo++) {
            String requestUri = buildDataGoFileDataRequestUri(
                    sourceConfig,
                    publicDataPk,
                    publicDataDetailPk,
                    fileDataPageNo,
                    filterField,
                    filterValue
            );
            String responseBody = fetchBody(requestUri, sourceConfig);
            FileDataPageResultDto pageResult = parseDataGoFileDataItems(responseBody, sourceConfig);

            log.info("[COUNT] source={} {} page={} detected={}",
                    sourceConfig.getSourceType(),
                    contextLabel,
                    fileDataPageNo,
                    pageResult.items().size());

            if (pageResult.items().isEmpty()) {
                break;
            }

            records.addAll(pageResult.items());

            if (!hasNextPage(pageResult.totalCount(), fileDataPageNo, sourceConfig.getPageSize(), pageResult.items().size())) {
                break;
            }
        }

        return records;
    }

    private FileDataPageResultDto parseDataGoFileDataItems(
            String responseBody,
            BridgeWorkSyncProperties.SourceConfig sourceConfig
    ) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            List<PublicDataApiItemDto> items = mapItems(rootNode.path("data"), sourceConfig);
            int totalCount = parseInteger(rootNode.path("totalCount").asText(null)).orElse(-1);
            return new FileDataPageResultDto(items, totalCount);
        } catch (JsonProcessingException exception) {
            ExternalApiException apiError = classifyBodyLevelApiError(responseBody, sourceConfig.getSourceType());
            if (apiError != null) {
                throw apiError;
            }
            throw new PayloadParseException("파일데이터 변환 API 응답 파싱 실패: " + sourceConfig.getSourceType(), exception);
        }
    }

    private PublicDataApiPageResponseDto fetchSeoulOpenApiPage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        String serviceName = Optional.ofNullable(sourceConfig.getQueryParams())
                .map(queryParams -> queryParams.get("serviceName"))
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> new ExternalApiException("서울 열린데이터 serviceName 설정이 비어 있습니다: " + sourceConfig.getSourceType()));

        String requestUri = buildSeoulOpenApiRequestUri(sourceConfig, serviceName, pageNo);
        String responseBody = fetchBody(requestUri, sourceConfig);

        try {
            JsonNode rootNode = parseSeoulOpenApiResponseBody(responseBody, requestUri, sourceConfig);
            JsonNode serviceNode = resolveSeoulServiceNode(rootNode, serviceName);
            validateSeoulOpenApiResultOrThrow(serviceNode, sourceConfig.getSourceType());

            JsonNode rowsNode = serviceNode.path("row");
            List<PublicDataApiItemDto> items = mapItems(rowsNode, sourceConfig);
            int totalCount = parseInteger(serviceNode.path("list_total_count").asText(null)).orElse(-1);
            boolean hasNext = hasNextPage(totalCount, pageNo, sourceConfig.getPageSize(), items.size());

            log.info("[COUNT] source={} serviceName={} page={} detected={} hasNext={}",
                    sourceConfig.getSourceType(),
                    serviceName,
                    pageNo,
                    items.size(),
                    hasNext);
            return new PublicDataApiPageResponseDto(items, hasNext);
        } catch (ExternalApiException exception) {
            throw exception;
        } catch (PayloadParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PayloadParseException("서울 열린데이터 응답 파싱 실패: " + sourceConfig.getSourceType(), exception);
        }
    }

    private JsonNode parseSeoulOpenApiResponseBody(
            String responseBody,
            String requestUri,
            BridgeWorkSyncProperties.SourceConfig sourceConfig
    ) {
        String trimmedBody = responseBody == null ? "" : responseBody.stripLeading();
        if (trimmedBody.isBlank()) {
            throw new ExternalApiException("서울 열린데이터 API 응답이 비어 있습니다: " + sourceConfig.getSourceType());
        }

        try {
            return objectMapper.readTree(trimmedBody);
        } catch (JsonProcessingException jsonException) {
            if (!trimmedBody.startsWith("<")) {
                throw new PayloadParseException(
                        "서울 열린데이터 응답 파싱 실패: "
                                + sourceConfig.getSourceType()
                                + " preview=" + buildPayloadPreview(trimmedBody),
                        jsonException
                );
            }

            if (looksLikeHtmlDocument(trimmedBody)) {
                String message = "서울 열린데이터 API가 JSON 대신 HTML 응답을 반환했습니다: "
                        + sourceConfig.getSourceType()
                        + " uri=" + maskCredentialInUri(requestUri, sourceConfig.getServiceKey())
                        + " preview=" + buildPayloadPreview(trimmedBody);
                if (looksRetryableErrorMessage(trimmedBody)) {
                    throw ExternalApiException.retryable(message);
                }
                throw new ExternalApiException(message);
            }

            // 일부 구간에서 XML로 내려오는 경우가 있어 안전하게 XML 파싱으로 복구한다.
            return parseXml(
                    trimmedBody,
                    "서울 열린데이터 XML 응답 파싱 실패: "
                            + sourceConfig.getSourceType()
                            + " preview=" + buildPayloadPreview(trimmedBody)
            );
        }
    }

    private boolean looksLikeHtmlDocument(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        String lower = payload.stripLeading().toLowerCase(Locale.ROOT);
        return lower.startsWith("<!doctype html")
                || lower.startsWith("<html")
                || lower.contains("<head")
                || lower.contains("<body");
    }

    private String buildPayloadPreview(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        String normalized = payload.replaceAll("\\s+", " ").trim();
        int maxLength = 180;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private JsonNode resolveSeoulServiceNode(JsonNode rootNode, String serviceName) {
        JsonNode configuredNode = rootNode.path(serviceName);
        if (configuredNode.isObject()) {
            return configuredNode;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode candidateNode = entry.getValue();
            if (!candidateNode.isObject()) {
                continue;
            }
            if (candidateNode.has("row") || candidateNode.has("list_total_count") || candidateNode.has("RESULT")) {
                return candidateNode;
            }
        }

        return objectMapper.createObjectNode();
    }

    private void validateSeoulOpenApiResultOrThrow(JsonNode serviceNode, PublicDataSourceType sourceType) {
        JsonNode resultNode = extractSeoulResultNode(serviceNode);
        String resultCode = normalizeResultCode(resultNode.path("CODE").asText(""));
        if (resultCode.isBlank() || isSuccessResultCode(resultCode) || isNoDataResultCode(sourceType, resultCode)) {
            return;
        }

        String message = resultNode.path("MESSAGE").asText("알 수 없는 오류").trim();
        throw buildApiErrorException(sourceType, resultCode, message, "서울 열린데이터 API 응답 오류");
    }

    private JsonNode extractSeoulResultNode(JsonNode serviceNode) {
        if (!serviceNode.isObject()) {
            return objectMapper.createObjectNode();
        }

        JsonNode nestedResultNode = serviceNode.path("RESULT");
        if (nestedResultNode.isObject()) {
            return nestedResultNode;
        }

        if (serviceNode.has("CODE") || serviceNode.has("MESSAGE")) {
            return serviceNode;
        }

        return objectMapper.createObjectNode();
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
            String responseBody = fetchBody(requestUri, sourceConfig);

            try {
                JsonNode rootNode = objectMapper.readTree(responseBody);
                validateApiResultOrThrow(rootNode, sourceConfig.getSourceType());
                JsonNode itemsNode = resolveRailItemsNode(rootNode, sourceConfig.getItemsJsonPointer());
                List<PublicDataApiItemDto> stationItems = mapRailItems(
                        itemsNode,
                        stationReference,
                        sourceConfig.getSourceType()
                );
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
                        "국가철도공단 역사별 휠체어리프트 응답 파싱 실패: "
                                + stationReference.railOprIsttCd() + "/" + stationReference.lnCd() + "/" + stationReference.stinCd(),
                        exception
                );
            }
        }

        return new PublicDataApiPageResponseDto(aggregatedItems, false);
    }

    private PublicDataApiPageResponseDto fetchDataGoXmlPage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        String requestUri = buildRequestUri(sourceConfig, pageNo);
        String responseBody = fetchBody(requestUri, sourceConfig);
        JsonNode rootNode = parseXml(responseBody, "공공데이터 XML 응답 파싱 실패: " + sourceConfig.getSourceType());

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
    }

    private PublicDataApiPageResponseDto fetchVocationalTrainingPage(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo
    ) {
        String requestUri = buildVocationalTrainingRequestUri(sourceConfig, pageNo);
        String responseBody = fetchBody(requestUri, sourceConfig);
        JsonNode rootNode = parseXml(responseBody, "직업훈련 API 응답 파싱 실패");

        JsonNode itemsNode = resolveFirstNodeByPointers(
                rootNode,
                "/srchList/scn_list",
                "/HRDNet/srchList/scn_list",
                "/response/srchList/scn_list",
                "/response/HRDNet/srchList/scn_list",
                normalizeJsonPointer(sourceConfig.getItemsJsonPointer())
        );
        List<PublicDataApiItemDto> items = mapItems(itemsNode, sourceConfig);
        int totalCount = resolveFirstIntegerByPointers(
                rootNode,
                "/scn_cnt",
                "/HRDNet/scn_cnt",
                "/response/scn_cnt",
                "/response/HRDNet/scn_cnt",
                normalizeJsonPointer(sourceConfig.getTotalCountJsonPointer())
        ).orElse(-1);
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
                String responseBody = fetchBody(requestUri, sourceConfig);
                JsonNode rootNode = parseXml(
                        responseBody,
                        "구직자 취업역량 강화프로그램 API 응답 파싱 실패: pgmStdt=" + pgmStdt + ", startPage=" + datePageNo
                );

                String messageCd = extractFirstTextByPointers(
                        rootNode,
                        "/messageCd",
                        "/empPgmSchdInviteList/messageCd",
                        "/response/messageCd",
                        "/response/empPgmSchdInviteList/messageCd"
                ).orElse("");
                if ("006".equals(messageCd)) {
                    log.info("[COUNT] source={} pgmStdt={} page={} detected=0 (messageCd=006)",
                            sourceConfig.getSourceType(),
                            pgmStdt,
                            datePageNo);
                    break;
                }

                JsonNode itemsNode = resolveFirstNodeByPointers(
                        rootNode,
                        "/empPgmSchdInvite",
                        "/empPgmSchdInviteList/empPgmSchdInvite",
                        "/response/empPgmSchdInvite",
                        "/response/empPgmSchdInviteList/empPgmSchdInvite",
                        normalizeJsonPointer(sourceConfig.getItemsJsonPointer())
                );
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

                int totalCount = resolveFirstIntegerByPointers(
                        rootNode,
                        "/total",
                        "/empPgmSchdInviteList/total",
                        "/response/total",
                        "/response/empPgmSchdInviteList/total",
                        normalizeJsonPointer(sourceConfig.getTotalCountJsonPointer())
                ).orElse(-1);
                if (!hasNextPage(totalCount, datePageNo, sourceConfig.getPageSize(), datePageItems.size())) {
                    break;
                }
            }
        }

        return new PublicDataApiPageResponseDto(aggregatedItems, false);
    }

    private String buildRequestUri(BridgeWorkSyncProperties.SourceConfig sourceConfig, int pageNo) {
        String encodedServiceKey = resolveEncodedCredential(sourceConfig.getServiceKey(), "serviceKey", sourceConfig.getSourceType());
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", sourceConfig.getPageSize());

        appendJsonResponseTypeParam(builder, sourceConfig.getQueryParams());
        applyQueryParams(builder, sourceConfig.getQueryParams());
        String baseUri = builder.build().encode(StandardCharsets.UTF_8).toUriString();
        return appendEncodedQueryParam(baseUri, "serviceKey", encodedServiceKey);
    }

    private String buildDataGoFileDataRequestUri(BridgeWorkSyncProperties.SourceConfig sourceConfig,
                                                 String publicDataPk,
                                                 String publicDataDetailPk,
                                                 int pageNo,
                                                 String filterField,
                                                 String filterValue) {
        String encodedServiceKey = resolveEncodedCredential(sourceConfig.getServiceKey(), "serviceKey", sourceConfig.getSourceType());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl("https://api.odcloud.kr/api/" + publicDataPk + "/v1/" + publicDataDetailPk)
                .queryParam("page", pageNo)
                .queryParam("perPage", sourceConfig.getPageSize())
                .queryParam("returnType", "JSON");

        if (filterField != null && !filterField.isBlank() && filterValue != null && !filterValue.isBlank()) {
            // fileData 변환 API는 컬럼명 기반 필터를 지원한다.
            builder.queryParam(filterField.trim(), filterValue.trim());
        }

        applyQueryParams(builder, sourceConfig.getQueryParams());
        String baseUri = builder.build().encode(StandardCharsets.UTF_8).toUriString();
        return appendEncodedQueryParam(baseUri, "serviceKey", encodedServiceKey);
    }

    private String buildSeoulOpenApiRequestUri(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            String serviceName,
            int pageNo
    ) {
        int start = (pageNo - 1) * sourceConfig.getPageSize() + 1;
        int end = pageNo * sourceConfig.getPageSize();

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .pathSegment(sourceConfig.getServiceKey(), "json", serviceName, String.valueOf(start), String.valueOf(end));

        if (sourceConfig.getQueryParams() != null && !sourceConfig.getQueryParams().isEmpty()) {
            sourceConfig.getQueryParams().forEach((key, value) -> {
                if ("serviceName".equals(key)) {
                    return;
                }
                if (value != null && !value.isBlank()) {
                    builder.queryParam(key, value);
                }
            });
        }

        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private String buildRailWheelchairLiftRequestUri(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            KricStationCodeLoader.StationReference stationReference
    ) {
        String encodedServiceKey = resolveEncodedCredential(sourceConfig.getServiceKey(), "serviceKey", sourceConfig.getSourceType());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("railOprIsttCd", stationReference.railOprIsttCd())
                .queryParam("lnCd", stationReference.lnCd())
                .queryParam("stinCd", stationReference.stinCd());

        applyQueryParams(builder, sourceConfig.getQueryParams());
        String baseUri = builder.build().encode(StandardCharsets.UTF_8).toUriString();
        return appendEncodedQueryParam(baseUri, "serviceKey", encodedServiceKey);
    }

    private String buildVocationalTrainingRequestUri(BridgeWorkSyncProperties.SourceConfig sourceConfig, int pageNo) {
        String encodedAuthKey = resolveEncodedCredential(sourceConfig.getServiceKey(), "authKey", sourceConfig.getSourceType());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("returnType", "XML")
                .queryParam("pageNum", pageNo)
                .queryParam("pageSize", sourceConfig.getPageSize());

        applyQueryParams(builder, sourceConfig.getQueryParams());
        String baseUri = builder.build().encode(StandardCharsets.UTF_8).toUriString();
        return appendEncodedQueryParam(baseUri, "authKey", encodedAuthKey);
    }

    private String buildJobseekerCompetencyProgramRequestUri(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            int pageNo,
            String pgmStdt
    ) {
        String encodedAuthKey = resolveEncodedCredential(sourceConfig.getServiceKey(), "authKey", sourceConfig.getSourceType());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("returnType", "XML")
                .queryParam("startPage", pageNo)
                .queryParam("display", sourceConfig.getPageSize())
                .queryParam("pgmStdt", pgmStdt);

        applyQueryParams(builder, sourceConfig.getQueryParams());
        String baseUri = builder.build().encode(StandardCharsets.UTF_8).toUriString();
        return appendEncodedQueryParam(baseUri, "authKey", encodedAuthKey);
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
                        || "returnType".equalsIgnoreCase(key)
                        || "dataType".equalsIgnoreCase(key));

        if (!hasResponseTypeParam) {
            builder.queryParam("_type", "json");
        }
    }

    private String resolveEncodedCredential(String rawCredential,
                                            String credentialLabel,
                                            PublicDataSourceType sourceType) {
        if (rawCredential == null || rawCredential.isBlank()) {
            throw new ExternalApiException(credentialLabel + " 값이 비어 있습니다: " + sourceType);
        }
        String trimmed = rawCredential.trim();

        // 입력값이 Encoding 키(%2F...) 또는 Decoding 키(/, +, =) 어떤 형태든
        // 원문으로 정규화 후 queryParam으로 안전하게 1회 인코딩한다.
        String decoded = decodePercentEscapesSafely(trimmed);
        String normalized = URLEncoder.encode(decoded, StandardCharsets.UTF_8);

        log.debug("[CREDENTIAL] source={} label={} rawLength={} normalizedLength={} encodedForm={}",
                sourceType,
                credentialLabel,
                trimmed.length(),
                normalized.length(),
                trimmed.contains("%"));
        return normalized;
    }

    private String appendEncodedQueryParam(String uri, String name, String encodedValue) {
        String separator = uri.contains("?") ? "&" : "?";
        return uri + separator + name + "=" + encodedValue;
    }

    private String decodePercentEscapesSafely(String value) {
        if (value == null || value.isBlank() || !value.contains("%")) {
            return value;
        }
        try {
            // serviceKey는 '+'가 실제 문자일 수 있으므로 디코딩 시 공백 치환을 방지한다.
            String plusEscaped = value.replace("+", "%2B");
            return URLDecoder.decode(plusEscaped, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            // 잘못된 % escape 입력은 원문을 유지해 후속 인코딩에서 안전하게 처리한다.
            return value;
        }
    }

    private String fetchBody(String requestUri, BridgeWorkSyncProperties.SourceConfig sourceConfig) {
        PublicDataSourceType sourceType = sourceConfig.getSourceType();
        Duration requestTimeout = resolveRequestTimeout(sourceConfig);
        log.info("[HTTP] source={} uri={}", sourceType, maskCredentialInUri(requestUri, sourceConfig.getServiceKey()));
        return webClient
                .get()
                .uri(URI.create(requestUri))
                .retrieve()
                .onStatus(status -> status.value() == 429 || status.is5xxServerError(), clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> ExternalApiException.retryable(
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
                .timeout(requestTimeout)
                .retryWhen(
                        Retry.backoff(API_RETRY_COUNT, API_RETRY_BACKOFF)
                                .filter(this::isRetryableApiException)
                )
                .onErrorMap(TimeoutException.class, exception ->
                        ExternalApiException.retryable("공공데이터 API 호출 타임아웃(" + sourceType + "): "
                                + requestTimeout.toSeconds() + "초"))
                .blockOptional()
                .orElseThrow(() -> new ExternalApiException("공공데이터 API 응답이 비어 있습니다: " + sourceType));
    }

    private Duration resolveRequestTimeout(BridgeWorkSyncProperties.SourceConfig sourceConfig) {
        Duration sourceTimeout = sourceConfig.getRequestTimeout();
        if (sourceTimeout != null && !sourceTimeout.isNegative() && !sourceTimeout.isZero()) {
            return sourceTimeout;
        }
        return syncProperties.getRequestTimeout();
    }

    private String maskCredentialQueryParams(String uri) {
        if (uri == null || uri.isBlank()) {
            return uri;
        }
        Matcher matcher = CREDENTIAL_QUERY_PARAM_PATTERN.matcher(uri);
        return matcher.replaceAll("$1***");
    }

    private String maskCredentialInUri(String uri, String rawCredential) {
        String maskedUri = maskCredentialQueryParams(uri);
        if (maskedUri == null || maskedUri.isBlank() || rawCredential == null || rawCredential.isBlank()) {
            return maskedUri;
        }

        Set<String> candidates = new LinkedHashSet<>();
        String trimmed = rawCredential.trim();
        candidates.add(trimmed);

        String decoded = decodePercentEscapesSafely(trimmed);
        if (decoded != null && !decoded.isBlank()) {
            candidates.add(decoded);
            candidates.add(URLEncoder.encode(decoded, StandardCharsets.UTF_8));
        }
        candidates.add(URLEncoder.encode(trimmed, StandardCharsets.UTF_8));

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                maskedUri = maskedUri.replace(candidate, "***");
            }
        }
        return maskedUri;
    }

    private boolean isRetryableApiException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        Throwable current = throwable;
        while (current != null && current.getCause() != current) {
            if (current instanceof TimeoutException) {
                return true;
            }
            if (current instanceof ExternalApiException externalApiException
                    && externalApiException.isRetryable()) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                if (message.contains("재시도 대상")
                        || message.contains("Connection reset")
                        || message.contains("Read timed out")
                        || message.contains("Failed to resolve")
                        || message.contains("UnknownHostException")
                        || message.contains("SERVFAIL")
                        || message.contains("connection prematurely closed")
                        || message.contains("failed to respond")
                        || message.contains("failure when writing TLS control frames")
                        || message.contains("TLS")
                        || message.contains("SSL")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String extractFileDataField(Document document, String fieldName) {
        Element formElement = document.selectFirst("form#frmFile, form[name=frmFile]");
        Element inputElement = null;
        if (formElement != null) {
            inputElement = formElement.selectFirst("input#" + fieldName + ", input[name=" + fieldName + "]");
        }
        if (inputElement == null) {
            inputElement = document.selectFirst("input#" + fieldName + ", input[name=" + fieldName + "]");
        }
        if (inputElement == null) {
            throw new ExternalApiException("파일데이터 페이지에서 " + fieldName + " 추출 실패");
        }
        String value = inputElement.attr("value").trim();
        if (value.isBlank()) {
            throw new ExternalApiException("파일데이터 페이지에서 " + fieldName + " 값이 비어 있습니다.");
        }
        // fileData 페이지의 식별자를 그대로 사용해야 변환 OpenAPI의 최신 버전 경로를 고정할 수 있다.
        return value;
    }

    private JsonNode resolveItemsNode(JsonNode rootNode, String configuredPointer) {
        String pointer = (configuredPointer == null || configuredPointer.isBlank())
                ? DEFAULT_ITEMS_POINTER
                : configuredPointer;

        JsonNode itemsNode = rootNode.at(pointer);
        if (!itemsNode.isMissingNode() && !itemsNode.isNull()) {
            return itemsNode;
        }

        JsonNode bodyItemsNode = rootNode.path("response").path("body").path("items");
        if (!bodyItemsNode.isMissingNode() && !bodyItemsNode.isNull()) {
            // 표준데이터 API는 items가 배열로 내려오는 경우가 있어 item 하위 키 없이도 처리한다.
            if (bodyItemsNode.isArray()) {
                return bodyItemsNode;
            }
            JsonNode nestedItemNode = bodyItemsNode.path("item");
            if (!nestedItemNode.isMissingNode() && !nestedItemNode.isNull()) {
                return nestedItemNode;
            }
        }

        JsonNode fallbackItems = rootNode.path("response").path("body").path("items").path("item");
        if (!fallbackItems.isMissingNode() && !fallbackItems.isNull()) {
            return fallbackItems;
        }

        JsonNode bareBodyItemsNode = rootNode.path("body").path("items");
        if (!bareBodyItemsNode.isMissingNode() && !bareBodyItemsNode.isNull()) {
            if (bareBodyItemsNode.isArray()) {
                return bareBodyItemsNode;
            }
            JsonNode nestedItemNode = bareBodyItemsNode.path("item");
            if (!nestedItemNode.isMissingNode() && !nestedItemNode.isNull()) {
                return nestedItemNode;
            }
        }

        JsonNode bareFallbackItems = rootNode.path("body").path("items").path("item");
        if (!bareFallbackItems.isMissingNode() && !bareFallbackItems.isNull()) {
            return bareFallbackItems;
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

        JsonNode bareFallbackCount = rootNode.path("body").path("totalCount");
        if (!bareFallbackCount.isMissingNode()) {
            Optional<Integer> parsed = parseInteger(bareFallbackCount.asText(null));
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

    private String normalizeJsonPointer(String pointer) {
        if (pointer == null) {
            return "";
        }
        return pointer.trim();
    }

    private JsonNode resolveFirstNodeByPointers(JsonNode rootNode, String... pointers) {
        if (pointers == null || pointers.length == 0) {
            return objectMapper.createArrayNode();
        }

        for (String pointer : pointers) {
            if (pointer == null || pointer.isBlank()) {
                continue;
            }

            JsonNode resolvedNode = rootNode.at(pointer);
            if (!resolvedNode.isMissingNode() && !resolvedNode.isNull()) {
                return resolvedNode;
            }
        }

        return objectMapper.createArrayNode();
    }

    private Optional<Integer> resolveFirstIntegerByPointers(JsonNode rootNode, String... pointers) {
        if (pointers == null || pointers.length == 0) {
            return Optional.empty();
        }

        for (String pointer : pointers) {
            if (pointer == null || pointer.isBlank()) {
                continue;
            }

            JsonNode resolvedNode = rootNode.at(pointer);
            if (resolvedNode.isMissingNode() || resolvedNode.isNull()) {
                continue;
            }

            Optional<Integer> parsed = parseInteger(resolvedNode.asText(null));
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        return Optional.empty();
    }

    private Optional<String> extractFirstTextByPointers(JsonNode rootNode, String... pointers) {
        if (pointers == null || pointers.length == 0) {
            return Optional.empty();
        }

        for (String pointer : pointers) {
            if (pointer == null || pointer.isBlank()) {
                continue;
            }

            JsonNode resolvedNode = rootNode.at(pointer);
            if (resolvedNode.isMissingNode() || resolvedNode.isNull()) {
                continue;
            }

            String value = resolvedNode.asText("").trim();
            if (!value.isBlank()) {
                return Optional.of(value);
            }
        }

        return Optional.empty();
    }

    private List<PublicDataApiItemDto> mapRailItems(
            JsonNode itemsNode,
            KricStationCodeLoader.StationReference stationReference,
            PublicDataSourceType sourceType
    ) {
        List<PublicDataApiItemDto> items = new ArrayList<>();

        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                items.add(toRailItem(itemNode, stationReference, sourceType));
            }
            return items;
        }

        if (itemsNode.isObject()) {
            if (containsRailField(itemsNode)) {
                items.add(toRailItem(itemsNode, stationReference, sourceType));
                return items;
            }

            boolean hasWrapperKey = List.of("item", "items", "list", "row", "body", "data", "result", "response", "header")
                    .stream()
                    .anyMatch(itemsNode::has);
            if (!hasWrapperKey && itemsNode.size() > 0) {
                items.add(toRailItem(itemsNode, stationReference, sourceType));
                return items;
            }

            for (String nestedKey : List.of("item", "items", "list", "row", "body", "data", "result")) {
                JsonNode nestedNode = itemsNode.path(nestedKey);
                if (nestedNode.isArray()) {
                    for (JsonNode childNode : nestedNode) {
                        items.add(toRailItem(childNode, stationReference, sourceType));
                    }
                    if (!items.isEmpty()) {
                        return items;
                    }
                } else if (nestedNode.isObject()) {
                    if (containsRailField(nestedNode)) {
                        items.add(toRailItem(nestedNode, stationReference, sourceType));
                        return items;
                    }

                    List<PublicDataApiItemDto> nestedItems = mapRailItems(nestedNode, stationReference, sourceType);
                    if (!nestedItems.isEmpty()) {
                        items.addAll(nestedItems);
                        return items;
                    }
                }
            }
        }

        return items;
    }

    private PublicDataApiItemDto toRailItem(
            JsonNode rawItemNode,
            KricStationCodeLoader.StationReference stationReference,
            PublicDataSourceType sourceType
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
            String externalId = buildRailExternalId(normalizedItem, payloadHash, sourceType);
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

    private String buildRailExternalId(
            ObjectNode normalizedItem,
            String payloadHash,
            PublicDataSourceType sourceType
    ) {
        String identity = String.join("|",
                sourceType.name(),
                normalizedItem.path("railOprIsttCd").asText("").trim(),
                normalizedItem.path("lnCd").asText("").trim(),
                normalizedItem.path("stinCd").asText("").trim(),
                normalizedItem.path("exitNo").asText("").trim(),
                normalizedItem.path("runStinFlorFr").asText("").trim(),
                normalizedItem.path("runStinFlorTo").asText("").trim(),
                normalizedItem.path("dtlLoc").asText("").trim(),
                normalizedItem.path("grndDvNmFr").asText("").trim(),
                normalizedItem.path("grndDvNmTo").asText("").trim(),
                payloadHash
        );
        return sourceType.name().toLowerCase(Locale.ROOT) + "-" + sha256(identity);
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
        String resultCode = extractApiResultCode(rootNode);

        if (resultCode.isBlank() || isSuccessResultCode(resultCode) || isNoDataResultCode(sourceType, resultCode)) {
            return;
        }

        String resultMessage = extractApiResultMessage(rootNode);
        throw buildApiErrorException(sourceType, resultCode, resultMessage, "공공데이터 API 응답 오류");
    }

    private boolean isSuccessResultCode(String resultCode) {
        String normalized = normalizeResultCode(resultCode);
        return "00".equals(normalized)
                || "0000".equals(normalized)
                || "0".equals(normalized)
                || "NORMAL_CODE".equals(normalized)
                || "INFO-000".equals(normalized)
                || "SUCCESS".equals(normalized);
    }

    private boolean isNoDataResultCode(PublicDataSourceType sourceType, String resultCode) {
        String normalized = normalizeResultCode(resultCode);
        return "03".equals(normalized)
                || "NODATA_ERROR".equals(normalized)
                || "INFO-200".equals(normalized)
                || "006".equals(normalized);
    }

    private String extractApiResultCode(JsonNode rootNode) {
        return normalizeResultCode(extractTextAt(rootNode, "/response/header/resultCode")
                .or(() -> extractTextAt(rootNode, "/header/resultCode"))
                .or(() -> extractTextAt(rootNode, "/resultCode"))
                .or(() -> extractTextAt(rootNode, "/cmmMsgHeader/returnReasonCode"))
                .or(() -> extractTextAt(rootNode, "/RESULT/CODE"))
                .or(() -> extractTextAt(rootNode, "/messageCd"))
                .orElse(""));
    }

    private String extractApiResultMessage(JsonNode rootNode) {
        return extractTextAt(rootNode, "/response/header/resultMsg")
                .or(() -> extractTextAt(rootNode, "/header/resultMsg"))
                .or(() -> extractTextAt(rootNode, "/resultMsg"))
                .or(() -> extractTextAt(rootNode, "/cmmMsgHeader/errMsg"))
                .or(() -> extractTextAt(rootNode, "/RESULT/MESSAGE"))
                .or(() -> extractTextAt(rootNode, "/message"))
                .orElse("알 수 없는 오류");
    }

    private ExternalApiException buildApiErrorException(
            PublicDataSourceType sourceType,
            String resultCode,
            String resultMessage,
            String label
    ) {
        String normalizedCode = normalizeResultCode(resultCode);
        String normalizedMessage = normalizeMessage(resultMessage);
        String message = label + "(" + sourceType + "): "
                + normalizedMessage
                + " [resultCode=" + normalizedCode + "]";
        if (isRetryableResultCode(normalizedCode, normalizedMessage)) {
            return ExternalApiException.retryable(message);
        }
        return new ExternalApiException(message);
    }

    private String normalizeResultCode(String resultCode) {
        if (resultCode == null) {
            return "";
        }
        return resultCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "알 수 없는 오류";
        }
        return message.trim();
    }

    private boolean isRetryableResultCode(String resultCode, String resultMessage) {
        if (RETRYABLE_RESULT_CODES.contains(resultCode)) {
            return true;
        }
        if (PERMANENT_RESULT_CODES.contains(resultCode)) {
            return false;
        }
        if (looksRetryableErrorMessage(resultMessage)) {
            return true;
        }
        return false;
    }

    private boolean looksRetryableErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("일시")
                || lower.contains("temporar")
                || lower.contains("timeout")
                || lower.contains("time out")
                || lower.contains("연결")
                || lower.contains("server error")
                || lower.contains("db error")
                || lower.contains("database")
                || lower.contains("too many requests")
                || lower.contains("limit")
                || lower.contains("busy");
    }

    private ExternalApiException classifyBodyLevelApiError(String responseBody, PublicDataSourceType sourceType) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        String trimmedBody = responseBody.stripLeading();
        try {
            JsonNode jsonNode = objectMapper.readTree(trimmedBody);
            String resultCode = extractApiResultCode(jsonNode);
            if (resultCode.isBlank() || isSuccessResultCode(resultCode) || isNoDataResultCode(sourceType, resultCode)) {
                return null;
            }
            String resultMessage = extractApiResultMessage(jsonNode);
            return buildApiErrorException(sourceType, resultCode, resultMessage, "공공데이터 API 응답 오류");
        } catch (Exception ignored) {
            // JSON 파싱 실패 시 XML/HTML 형태를 추가 판단한다.
        }

        if (!trimmedBody.startsWith("<")) {
            return null;
        }
        if (looksLikeHtmlDocument(trimmedBody)) {
            String message = "공공데이터 API가 HTML 오류 문서를 반환했습니다: "
                    + sourceType
                    + " preview=" + buildPayloadPreview(trimmedBody);
            if (looksRetryableErrorMessage(trimmedBody)) {
                return ExternalApiException.retryable(message);
            }
            return new ExternalApiException(message);
        }

        try {
            JsonNode xmlNode = xmlMapper.readTree(trimmedBody.getBytes(StandardCharsets.UTF_8));
            String resultCode = extractApiResultCode(xmlNode);
            if (resultCode.isBlank() || isSuccessResultCode(resultCode) || isNoDataResultCode(sourceType, resultCode)) {
                return null;
            }
            String resultMessage = extractApiResultMessage(xmlNode);
            return buildApiErrorException(sourceType, resultCode, resultMessage, "공공데이터 API 응답 오류");
        } catch (Exception ignored) {
            return null;
        }
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

    private record FileDataPageResultDto(
            List<PublicDataApiItemDto> items,
            int totalCount
    ) {
    }

    private record DataGoFileDataVersion(
            String publicDataPk,
            String publicDataDetailPk,
            String revisionKey,
            String displayName,
            LocalDate modifiedDate
    ) {
    }

    private record DataGoFileDataCandidate(
            String publicDataPk,
            String publicDataDetailPk,
            LocalDate modifiedDate,
            String contextLabel
    ) {
    }

    private record OpenApiPage(
            String url,
            String body
    ) {
    }

    private record SeoulDatasetLatestFile(
            String infId,
            String infSeq,
            String seq,
            String fileName,
            String modifiedDateText,
            LocalDate modifiedDate,
            String revisionKey
    ) {
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
