package com.bridgework.sync.normalized;

import com.bridgework.sync.entity.PublicDataSourceType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record NormalizedSourceDefinition(
        PublicDataSourceType sourceType,
        String tableName,
        List<NormalizedColumnMapping> columnMappings,
        String geocodeAddressField,
        String geocodeLatitudeColumn,
        String geocodeLongitudeColumn,
        String geocodeMatchedAddressColumn,
        String geocodeOriginalAddressColumn
) {

    public record NormalizedColumnMapping(String sourceField, String columnName) {
    }

    public Optional<NormalizedColumnMapping> findBySourceField(String sourceField) {
        return columnMappings.stream().filter(mapping -> mapping.sourceField().equals(sourceField)).findFirst();
    }

    public Map<String, String> sourceToColumnMap() {
        return columnMappings.stream().collect(java.util.stream.Collectors.toMap(
                NormalizedColumnMapping::sourceField,
                NormalizedColumnMapping::columnName,
                (left, right) -> left,
                java.util.LinkedHashMap::new
        ));
    }

    public boolean usesGeocoding() {
        return geocodeAddressField != null && !geocodeAddressField.isBlank();
    }
}
