package com.bridgework.notice.controller;

import com.bridgework.common.dto.ApiResponse;
import com.bridgework.notice.dto.NoticeRequestDto;
import com.bridgework.notice.dto.NoticeResponseDto;
import com.bridgework.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notices")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Notice", description = "관리자 공지사항 API")
public class AdminNoticeController {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    @Operation(summary = "관리자 공지사항 목록 조회", description = "공개/비공개 공지사항을 모두 조회한다.")
    public ResponseEntity<ApiResponse<List<NoticeResponseDto>>> getAdminNotices(
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.getAdminNotices(limit)));
    }

    @GetMapping("/{noticeId}")
    @Operation(summary = "관리자 공지사항 상세 조회")
    public ResponseEntity<ApiResponse<NoticeResponseDto>> getAdminNotice(@PathVariable Long noticeId) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.getAdminNotice(noticeId)));
    }

    @PostMapping
    @Operation(summary = "공지사항 생성")
    public ResponseEntity<ApiResponse<NoticeResponseDto>> createNotice(@Valid @RequestBody NoticeRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.createNotice(request)));
    }

    @PutMapping("/{noticeId}")
    @Operation(summary = "공지사항 수정")
    public ResponseEntity<ApiResponse<NoticeResponseDto>> updateNotice(
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.updateNotice(noticeId, request)));
    }

    @DeleteMapping("/{noticeId}")
    @Operation(summary = "공지사항 삭제")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(@PathVariable Long noticeId) {
        noticeService.deleteNotice(noticeId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
