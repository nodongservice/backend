package com.bridgework.sync.dto;

public record SourceLatestRevisionDto(
        String revisionKey,
        String fileName,
        String modifiedDate
) {
}
