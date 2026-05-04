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
import org.springframework.web.util.UriUtils;
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
    private static final Pattern PUBLIC_DATA_PK_CANDIDATE_PATTERN =
            Pattern.compile("publicDataPk[^0-9]*([0-9]{5,})");
    private static final Pattern PUBLIC_DATA_DETAIL_PK_CANDIDATE_PATTERN =
            Pattern.compile("publicDataDetailPk[^0-9]*([0-9]{5,})");
    private static final Pattern ODCLOUD_ENDPOINT_PATTERN =
            Pattern.compile("api\\.odcloud\\.kr/api/([0-9]{5,})/v1/([0-9]{5,})");
    private static final Pattern DATE_TOKEN_PATTERN =
            Pattern.compile("(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final LocalDate UNKNOWN_MODIFIED_DATE = LocalDate.of(1970, 1, 1);
    private static final DateTimeFormatter SEOUL_FILE_MODIFIED_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String SEOUL_FILE_DOWNLOAD_URL = "https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do?useCache=false";

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
        if (sourceType == PublicDataSourceType.NATIONWIDE_TRAFFIC_LIGHT) {
            return fetchDataGoXmlPage(sourceConfig, pageNo);
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

        for (String stationName : stationNames) {
            List<PublicDataApiItemDto> stationItems = fetchAllDataGoFileDataItems(
                    sourceConfig,
                    publicDataPk,
                    publicDataDetailPk,
                    "역명",
                    stationName,
                    "station=" + stationName
            );

            for (PublicDataApiItemDto stationItem : stationItems) {
                if (dedupedExternalIds.add(stationItem.externalId())) {
                    aggregatedItems.add(stationItem);
                }
            }
        }

        if (!aggregatedItems.isEmpty()) {
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
        String fileDataPageBody = fetchBody(sourceConfig.getBaseUrl(), sourceConfig.getSourceType());
        String fallbackPublicDataPk = extractFileDataField(fileDataPageBody, PUBLIC_DATA_PK_PATTERN, "publicDataPk");
        String fallbackPublicDataDetailPk = extractFileDataField(fileDataPageBody, PUBLIC_DATA_DETAIL_PK_PATTERN, "publicDataDetailPk");

        List<DataGoFileDataCandidate> candidates = resolveDataGoFileDataCandidates(
                fileDataPageBody,
                fallbackPublicDataPk,
                sourceConfig.getSourceType()
        );
        DataGoFileDataCandidate selected = candidates.stream()
                .max(Comparator
                        .comparing((DataGoFileDataCandidate candidate) -> candidate.modifiedDate() != null)
                        .thenComparing(candidate -> Optional.ofNullable(candidate.modifiedDate()).orElse(UNKNOWN_MODIFIED_DATE))
                        .thenComparingInt(candidate -> parseIntSafe(candidate.publicDataDetailPk())))
                .orElse(new DataGoFileDataCandidate(
                        fallbackPublicDataPk,
                        fallbackPublicDataDetailPk,
                        null,
                        "fallback-hidden-input"
                ));

        LocalDate modifiedDate = Optional.ofNullable(selected.modifiedDate()).orElse(UNKNOWN_MODIFIED_DATE);
        String revisionDate = selected.modifiedDate() == null ? "unknown" : modifiedDate.toString();
        String displayName = "publicDataDetailPk=" + selected.publicDataDetailPk();
        String revisionKey = revisionDate + "|" + selected.publicDataPk() + "|" + selected.publicDataDetailPk();

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
                                                                          String fallbackPublicDataPk,
                                                                          PublicDataSourceType sourceType) {
        Document document = Jsoup.parse(htmlBody);
        List<DataGoFileDataCandidate> candidates = new ArrayList<>();

        for (Element rowElement : document.select("tr")) {
            LocalDate modifiedDate = extractFirstDate(rowElement.text()).orElse(null);
            collectDataGoCandidatesFromText(
                    rowElement.outerHtml(),
                    fallbackPublicDataPk,
                    modifiedDate,
                    "table-row",
                    candidates
            );
        }

        collectDataGoCandidatesFromText(
                htmlBody,
                fallbackPublicDataPk,
                null,
                "page-body",
                candidates
        );

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
            log.warn("[FILEDATA] source={} 최신 API 후보를 찾지 못해 hidden input 식별자를 사용한다.", sourceType);
        }
        return new ArrayList<>(dedupedCandidates.values());
    }

    private void collectDataGoCandidatesFromText(String text,
                                                 String fallbackPublicDataPk,
                                                 LocalDate modifiedDate,
                                                 String contextLabel,
                                                 List<DataGoFileDataCandidate> candidates) {
        Matcher endpointMatcher = ODCLOUD_ENDPOINT_PATTERN.matcher(text);
        while (endpointMatcher.find()) {
            String publicDataPk = endpointMatcher.group(1).trim();
            String publicDataDetailPk = endpointMatcher.group(2).trim();
            candidates.add(new DataGoFileDataCandidate(publicDataPk, publicDataDetailPk, modifiedDate, contextLabel));
        }

        List<String> detailPkValues = extractRegexMatches(text, PUBLIC_DATA_DETAIL_PK_CANDIDATE_PATTERN);
        if (detailPkValues.isEmpty()) {
            return;
        }

        List<String> publicDataPkValues = extractRegexMatches(text, PUBLIC_DATA_PK_CANDIDATE_PATTERN);
        String publicDataPk = publicDataPkValues.isEmpty() ? fallbackPublicDataPk : publicDataPkValues.get(0);
        for (String publicDataDetailPk : detailPkValues) {
            candidates.add(new DataGoFileDataCandidate(publicDataPk, publicDataDetailPk, modifiedDate, contextLabel));
        }
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
        String htmlBody = fetchBody(sourceConfig.getBaseUrl(), sourceConfig.getSourceType());

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
            String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());
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
        String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
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
        } catch (JsonProcessingException exception) {
            throw new PayloadParseException("서울 열린데이터 응답 파싱 실패: " + sourceConfig.getSourceType(), exception);
        }
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
        String resultCode = resultNode.path("CODE").asText("").trim();
        if (resultCode.isBlank()
                || "INFO-000".equalsIgnoreCase(resultCode)
                || "INFO-200".equalsIgnoreCase(resultCode)) {
            return;
        }

        String message = resultNode.path("MESSAGE").asText("알 수 없는 오류").trim();
        throw new ExternalApiException(
                "서울 열린데이터 API 응답 오류(" + sourceType + "): "
                        + message
                        + " [CODE=" + resultCode + "]"
        );
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
            String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());

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
        String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());
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
        String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());
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
                String responseBody = fetchBody(requestUri, sourceConfig.getSourceType());
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
                .queryParam("serviceKey", encodedServiceKey)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", sourceConfig.getPageSize());

        appendJsonResponseTypeParam(builder, sourceConfig.getQueryParams());
        applyQueryParams(builder, sourceConfig.getQueryParams());
        return builder.build(true).toUriString();
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
                .queryParam("serviceKey", encodedServiceKey)
                .queryParam("page", pageNo)
                .queryParam("perPage", sourceConfig.getPageSize())
                .queryParam("returnType", "JSON");

        if (filterField != null && !filterField.isBlank() && filterValue != null && !filterValue.isBlank()) {
            // fileData 변환 API는 컬럼명 기반 필터를 지원한다.
            String encodedFilterField = UriUtils.encodeQueryParam(filterField.trim(), StandardCharsets.UTF_8);
            String encodedFilterValue = UriUtils.encodeQueryParam(filterValue.trim(), StandardCharsets.UTF_8);
            builder.queryParam(encodedFilterField, encodedFilterValue);
        }

        applyQueryParams(builder, sourceConfig.getQueryParams());
        return builder.build(true).toUriString();
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

        return builder.build(true).toUriString();
    }

    private String buildRailWheelchairLiftRequestUri(
            BridgeWorkSyncProperties.SourceConfig sourceConfig,
            KricStationCodeLoader.StationReference stationReference
    ) {
        String encodedServiceKey = resolveEncodedCredential(sourceConfig.getServiceKey(), "serviceKey", sourceConfig.getSourceType());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("serviceKey", encodedServiceKey)
                .queryParam("railOprIsttCd", stationReference.railOprIsttCd())
                .queryParam("lnCd", stationReference.lnCd())
                .queryParam("stinCd", stationReference.stinCd());

        applyQueryParams(builder, sourceConfig.getQueryParams());
        return builder.build(true).toUriString();
    }

    private String buildVocationalTrainingRequestUri(BridgeWorkSyncProperties.SourceConfig sourceConfig, int pageNo) {
        String encodedAuthKey = resolveEncodedCredential(sourceConfig.getServiceKey(), "authKey", sourceConfig.getSourceType());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("authKey", encodedAuthKey)
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
        String encodedAuthKey = resolveEncodedCredential(sourceConfig.getServiceKey(), "authKey", sourceConfig.getSourceType());
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sourceConfig.getBaseUrl())
                .queryParam("authKey", encodedAuthKey)
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
        String decoded;
        try {
            decoded = UriUtils.decode(trimmed, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            // 일부 키는 % 인코딩이 아닌 원문으로 저장될 수 있어 원문을 그대로 사용한다.
            decoded = trimmed;
        }

        return UriUtils.encodeQueryParam(decoded, StandardCharsets.UTF_8);
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
        if (throwable == null) {
            return false;
        }

        Throwable current = throwable;
        while (current != null && current.getCause() != current) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                if (message.contains("재시도 대상")
                        || message.contains("Connection reset")
                        || message.contains("Read timed out")
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
        if (sourceType != PublicDataSourceType.RAIL_WHEELCHAIR_LIFT
                && sourceType != PublicDataSourceType.RAIL_WHEELCHAIR_LIFT_MOVEMENT) {
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
