package com.bridgework.posting.dto;

public record ScrapCommandResponseDto(
        Long postingId,
        boolean scrapped
) {
}
