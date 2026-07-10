package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "수상 항목")
public record ProfileAwardEntryDto(
        String awardName,
        String awardingOrganization,
        String awardYear,
        String awardDescription
) {
}
