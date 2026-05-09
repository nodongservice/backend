package com.bridgework.admin.dummy.dto;

public record AdminDummyProfileOptionDto(
        Long profileId,
        String profileKey,
        String profileLabel,
        String scenarioSummary,
        boolean isDefault
) {
}

