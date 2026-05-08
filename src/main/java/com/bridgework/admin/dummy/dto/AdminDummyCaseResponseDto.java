package com.bridgework.admin.dummy.dto;

import java.util.List;

public record AdminDummyCaseResponseDto(
        String dummyKey,
        String displayName,
        String scenarioSummary,
        List<AdminDummyProfileOptionDto> profiles
) {
}

