package com.bridgework.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "수상 항목")
public record ProfileAwardEntryDto(
        @Size(max = 300) String awardName,
        @Size(max = 300) String awardingOrganization,
        @Size(max = 20) String awardYear,
        @Size(max = 2000) String awardDescription
) {
}
