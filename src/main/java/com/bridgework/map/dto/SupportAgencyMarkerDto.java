package com.bridgework.map.dto;

public record SupportAgencyMarkerDto(
        String externalId,
        String institutionCode,
        String institutionName,
        String address,
        String telephone,
        Double latitude,
        Double longitude
) {
}
