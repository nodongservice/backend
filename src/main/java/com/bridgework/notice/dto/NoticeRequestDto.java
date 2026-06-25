package com.bridgework.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeRequestDto(
        @NotBlank
        @Size(max = 160)
        @Schema(description = "공지사항 제목", example = "서비스 점검 안내")
        String title,
        @NotBlank
        @Size(max = 10000)
        @Schema(description = "공지사항 본문", example = "서비스 점검 시간 동안 일부 기능 이용이 제한됩니다.")
        String content,
        @Schema(description = "상단 고정 여부", example = "true")
        Boolean pinned,
        @Schema(description = "공개 여부", example = "true")
        Boolean published
) {
}
