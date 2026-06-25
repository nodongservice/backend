package com.bridgework.notice.dto;

import com.bridgework.notice.entity.Notice;
import java.time.OffsetDateTime;

public record NoticeResponseDto(
        Long id,
        String title,
        String content,
        boolean pinned,
        boolean published,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static NoticeResponseDto from(Notice notice) {
        return new NoticeResponseDto(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                notice.isPublished(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
