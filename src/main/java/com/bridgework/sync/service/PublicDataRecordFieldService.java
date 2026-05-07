package com.bridgework.sync.service;

import com.bridgework.sync.entity.PublicDataRecord;
import com.bridgework.sync.entity.PublicDataRecordField;
import com.bridgework.sync.exception.PayloadParseException;
import com.bridgework.sync.repository.PublicDataRecordFieldRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicDataRecordFieldService {

    private static final int MAX_PATH_LENGTH = 255;

    private final ObjectMapper objectMapper;
    private final PublicDataRecordFieldRepository publicDataRecordFieldRepository;

    public PublicDataRecordFieldService(ObjectMapper objectMapper,
                                        PublicDataRecordFieldRepository publicDataRecordFieldRepository) {
        this.objectMapper = objectMapper;
        this.publicDataRecordFieldRepository = publicDataRecordFieldRepository;
    }

    @Transactional
    public void replaceFields(PublicDataRecord record) {
        publicDataRecordFieldRepository.deleteByRecord_Id(record.getId());
        // 동일 트랜잭션에서 재삽입 전에 삭제 SQL을 먼저 반영해 유니크 충돌을 막는다.
        publicDataRecordFieldRepository.flush();

        List<PublicDataRecordField> fields = new ArrayList<>();
        JsonNode payloadNode = parsePayload(record.getPayloadJson());
        flattenNode(record, payloadNode, "", fields);

        if (!fields.isEmpty()) {
            publicDataRecordFieldRepository.saveAll(deduplicateByFieldPath(fields));
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<String, String>> getFieldMapByRecordIds(Collection<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }

        List<PublicDataRecordField> fields = publicDataRecordFieldRepository.findByRecord_IdIn(recordIds);
        Map<Long, Map<String, String>> fieldMapByRecordId = new LinkedHashMap<>();

        for (PublicDataRecordField field : fields) {
            Long recordId = field.getRecord().getId();
            fieldMapByRecordId
                    .computeIfAbsent(recordId, ignored -> new LinkedHashMap<>())
                    .put(field.getFieldPath(), field.getFieldValue());
        }

        return fieldMapByRecordId;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getFieldMapByRecordId(Long recordId) {
        List<PublicDataRecordField> fields = publicDataRecordFieldRepository.findByRecord_IdOrderByFieldPathAsc(recordId);
        Map<String, String> fieldMap = new LinkedHashMap<>();

        for (PublicDataRecordField field : fields) {
            fieldMap.put(field.getFieldPath(), field.getFieldValue());
        }

        return fieldMap;
    }

    private JsonNode parsePayload(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception exception) {
            throw new PayloadParseException("저장 payload를 필드 단위로 파싱하지 못했습니다.", exception);
        }
    }

    private void flattenNode(PublicDataRecord record,
                             JsonNode node,
                             String currentPath,
                             List<PublicDataRecordField> fields) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String nextPath = currentPath.isBlank() ? entry.getKey() : currentPath + "." + entry.getKey();
                flattenNode(record, entry.getValue(), nextPath, fields);
            });
            return;
        }

        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                String nextPath = currentPath + "[" + index + "]";
                flattenNode(record, node.get(index), nextPath, fields);
            }
            return;
        }

        PublicDataRecordField field = new PublicDataRecordField();
        field.setRecord(record);
        field.setFieldPath(normalizePath(currentPath));
        field.setFieldValue(node.isNull() ? null : node.asText());
        field.setValueType(resolveValueType(node));
        fields.add(field);
    }

    private String normalizePath(String path) {
        if (path.length() <= MAX_PATH_LENGTH) {
            return path;
        }

        String hash = sha256(path).substring(0, 16);
        return path.substring(0, MAX_PATH_LENGTH - 17) + "_" + hash;
    }

    private String resolveValueType(JsonNode node) {
        if (node.isTextual()) {
            return "TEXT";
        }
        if (node.isNumber()) {
            return "NUMBER";
        }
        if (node.isBoolean()) {
            return "BOOLEAN";
        }
        if (node.isNull()) {
            return "NULL";
        }
        return "UNKNOWN";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private List<PublicDataRecordField> deduplicateByFieldPath(List<PublicDataRecordField> fields) {
        Map<String, PublicDataRecordField> deduplicated = new LinkedHashMap<>();
        for (PublicDataRecordField field : fields) {
            deduplicated.put(field.getFieldPath(), field);
        }
        return new ArrayList<>(deduplicated.values());
    }
}
