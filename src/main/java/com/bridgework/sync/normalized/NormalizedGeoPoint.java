package com.bridgework.sync.normalized;

public record NormalizedGeoPoint(
        Double latitude,
        Double longitude,
        String matchedAddress
) {
}
