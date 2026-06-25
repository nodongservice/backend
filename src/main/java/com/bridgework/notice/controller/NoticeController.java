package com.bridgework.notice.controller;

import com.bridgework.common.dto.ApiResponse;
import com.bridgework.notice.dto.NoticeResponseDto;
import com.bridgework.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices")
@Tag(name = "Notice", description = "공지사항 공개 API")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    @Operation(summary = "공지사항 목록 조회", description = "공개 상태의 공지사항을 상단 고정/최신순으로 조회한다.")
    public ResponseEntity<ApiResponse<List<NoticeResponseDto>>> getNotices(
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.getPublicNotices(limit)));
    }

    @GetMapping("/{noticeId}")
    @Operation(summary = "공지사항 상세 조회", description = "공개 상태의 공지사항 상세를 조회한다.")
    public ResponseEntity<ApiResponse<NoticeResponseDto>> getNotice(@PathVariable Long noticeId) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.getPublicNotice(noticeId)));
    }
}
