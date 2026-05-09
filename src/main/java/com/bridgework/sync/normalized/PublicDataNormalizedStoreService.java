package com.bridgework.sync.normalized;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.dto.PublicDataApiItemDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.exception.ExternalApiException;
import com.bridgework.sync.exception.PayloadParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicDataNormalizedStoreService {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)$");
    private static final Set<String> COORDINATE_COLUMNS = Set.of("latitude", "longitude", "geo_latitude", "geo_longitude");

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final NormalizedSourceRegistry normalizedSourceRegistry;
    private final NaverGeocodingService naverGeocodingService;
    private final BridgeWorkSyncProperties syncProperties;
    private final Map<PublicDataSourceType, String> upsertSqlCache = new HashMap<>();

    public PublicDataNormalizedStoreService(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                            ObjectMapper objectMapper,
                                            NormalizedSourceRegistry normalizedSourceRegistry,
                                            NaverGeocodingService naverGeocodingService,
                                            BridgeWorkSyncProperties syncProperties) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
        this.normalizedSourceRegistry = normalizedSourceRegistry;
        this.naverGeocodingService = naverGeocodingService;
        this.syncProperties = syncProperties;
    }

    @Transactional
    public void upsert(PublicDataSourceType sourceType, PublicDataApiItemDto item, OffsetDateTime rawFetchedAt) {
        NormalizedSourceDefinition definition = normalizedSourceRegistry.get(sourceType);
        if (definition == null) {
            return;
        }

        JsonNode payloadNode = parsePayload(item.payloadJson());
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("external_id", item.externalId());
        params.addValue("payload_hash", item.payloadHash());
        params.addValue("raw_fetched_at", rawFetchedAt);

        for (NormalizedSourceDefinition.NormalizedColumnMapping mapping : definition.columnMappings()) {
            String value = extractFieldValue(payloadNode, mapping.sourceField());
            params.addValue(mapping.columnName(), convertColumnValue(mapping.columnName(), value));
        }

        applyGeocoding(definition, payloadNode, params);

        String upsertSql = upsertSqlCache.computeIfAbsent(sourceType, ignored -> buildUpsertSql(definition));
        namedParameterJdbcTemplate.update(upsertSql, params);
    }

    @Transactional
    public void touch(PublicDataSourceType sourceType, String externalId, OffsetDateTime rawFetchedAt) {
        NormalizedSourceDefinition definition = normalizedSourceRegistry.get(sourceType);
        if (definition == null) {
            return;
        }

        namedParameterJdbcTemplate.update(
                "UPDATE " + definition.tableName() + " SET raw_fetched_at = :rawFetchedAt, updated_at = NOW() WHERE external_id = :externalId",
                new MapSqlParameterSource()
                        .addValue("rawFetchedAt", rawFetchedAt)
                        .addValue("externalId", externalId)
        );
    }

    @Transactional
    public int deleteMissing(PublicDataSourceType sourceType, Set<String> fetchedExternalIds) {
        NormalizedSourceDefinition definition = normalizedSourceRegistry.get(sourceType);
        if (definition == null) {
            return 0;
        }

        List<String> existingIds = namedParameterJdbcTemplate.query(
                "SELECT external_id FROM " + definition.tableName(),
                (resultSet, rowNum) -> resultSet.getString("external_id")
        );

        if (existingIds.isEmpty()) {
            return 0;
        }

        Set<String> fetchedIds = fetchedExternalIds == null ? Set.of() : fetchedExternalIds;
        List<String> deleteIds = new ArrayList<>();
        for (String existingId : existingIds) {
            if (!fetchedIds.contains(existingId)) {
                deleteIds.add(existingId);
            }
        }

        if (deleteIds.isEmpty()) {
            return 0;
        }

        int deletedCount = 0;
        int chunkSize = 500;
        for (int start = 0; start < deleteIds.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, deleteIds.size());
            List<String> chunk = deleteIds.subList(start, end);
            deletedCount += namedParameterJdbcTemplate.update(
                    "DELETE FROM " + definition.tableName() + " WHERE external_id IN (:externalIds)",
                    new MapSqlParameterSource("externalIds", chunk)
            );
        }

        return deletedCount;
    }

    @Transactional(readOnly = true)
    public long countBySource(PublicDataSourceType sourceType) {
        NormalizedSourceDefinition definition = normalizedSourceRegistry.get(sourceType);
        if (definition == null) {
            return 0L;
        }

        Long count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + definition.tableName(),
                new MapSqlParameterSource(),
                Long.class
        );
        return count == null ? 0L : count;
    }

    @Transactional(readOnly = true)
    public String resolveTableName(PublicDataSourceType sourceType) {
        NormalizedSourceDefinition definition = normalizedSourceRegistry.get(sourceType);
        if (definition == null) {
            return null;
        }
        return definition.tableName();
    }

    private JsonNode parsePayload(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception exception) {
            throw new PayloadParseException("정규화 저장용 payload 파싱 실패", exception);
        }
    }

    private String extractFieldValue(JsonNode payloadNode, String sourceField) {
        JsonNode fieldNode = payloadNode.get(sourceField);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        if (fieldNode.isTextual()) {
            String value = fieldNode.asText().trim();
            return value.isBlank() ? null : value;
        }
        return fieldNode.asText();
    }

    private void applyGeocoding(NormalizedSourceDefinition definition,
                                JsonNode payloadNode,
                                MapSqlParameterSource params) {
        if (!definition.usesGeocoding()) {
            return;
        }

        String originalAddress = extractFieldValue(payloadNode, definition.geocodeAddressField());
        params.addValue(definition.geocodeOriginalAddressColumn(), originalAddress);

        if (originalAddress == null || originalAddress.isBlank()) {
            throw new ExternalApiException("지오코딩 실패: 주소 값이 비어 있습니다.");
        }

        String naverApiKeyId = syncProperties.getNaverGeocodeApiKeyId();
        String naverApiKey = syncProperties.getNaverGeocodeApiKey();
        if (naverApiKeyId == null || naverApiKeyId.isBlank() || naverApiKey == null || naverApiKey.isBlank()) {
            throw new ExternalApiException("지오코딩 실패: NAVER_GEOCODE_API_KEY_ID 또는 NAVER_GEOCODE_API_KEY가 비어 있습니다.");
        }

        Optional<NormalizedGeoPoint> geoPoint = naverGeocodingService.geocode(
                naverApiKeyId,
                naverApiKey,
                originalAddress
        );

        if (geoPoint.isEmpty()) {
            throw new ExternalApiException("지오코딩 실패: 주소 매칭 결과가 없습니다. address=" + originalAddress);
        }

        params.addValue(definition.geocodeLatitudeColumn(), geoPoint.get().latitude());
        params.addValue(definition.geocodeLongitudeColumn(), geoPoint.get().longitude());
        params.addValue(definition.geocodeMatchedAddressColumn(), geoPoint.get().matchedAddress());
    }

    private Object convertColumnValue(String columnName, String rawValue) {
        if (!COORDINATE_COLUMNS.contains(columnName)) {
            return rawValue;
        }
        return parseCoordinate(rawValue);
    }

    private Double parseCoordinate(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!DECIMAL_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        try {
            return Double.valueOf(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String buildUpsertSql(NormalizedSourceDefinition definition) {
        StringBuilder columns = new StringBuilder("external_id, payload_hash, raw_fetched_at");
        StringBuilder values = new StringBuilder(":external_id, :payload_hash, :raw_fetched_at");
        StringBuilder updates = new StringBuilder("payload_hash = EXCLUDED.payload_hash, raw_fetched_at = EXCLUDED.raw_fetched_at, updated_at = NOW()");

        List<String> allColumns = new ArrayList<>();
        for (NormalizedSourceDefinition.NormalizedColumnMapping mapping : definition.columnMappings()) {
            allColumns.add(mapping.columnName());
        }

        if (definition.usesGeocoding()) {
            allColumns.add(definition.geocodeOriginalAddressColumn());
            allColumns.add(definition.geocodeMatchedAddressColumn());
            allColumns.add(definition.geocodeLatitudeColumn());
            allColumns.add(definition.geocodeLongitudeColumn());
        }

        Set<String> dedupedColumns = new HashSet<>();
        List<String> orderedColumns = new ArrayList<>();
        for (String column : allColumns) {
            if (column != null && !column.isBlank() && dedupedColumns.add(column)) {
                orderedColumns.add(column);
            }
        }

        for (String column : orderedColumns) {
            columns.append(", ").append(column);
            values.append(", :").append(column);
            updates.append(", ").append(column).append(" = EXCLUDED.").append(column);
        }

        return "INSERT INTO " + definition.tableName() + " (" + columns + ", created_at, updated_at) "
                + "VALUES (" + values + ", NOW(), NOW()) "
                + "ON CONFLICT (external_id) DO UPDATE SET " + updates;
    }
}
