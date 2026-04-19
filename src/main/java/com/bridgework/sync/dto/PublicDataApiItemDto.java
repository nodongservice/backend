package com.bridgework.sync.dto;

public record PublicDataApiItemDto(
        String externalId,
        String payloadJson,
        String payloadHash
) {
}
