package com.bridgework.sync.admin.controller;

import com.bridgework.sync.dto.PublicDataRecordPageResponseDto;
import com.bridgework.sync.dto.PublicDataRecordResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.service.PublicDataRecordQueryService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/public-data/records")
@Tag(name = "PublicDataRecord", description = "공공데이터 원본 저장 레코드 조회 API (관리자 전용)")
@SecurityRequirement(name = "bearerAuth")
public class PublicDataRecordController {

    private final PublicDataRecordQueryService publicDataRecordQueryService;

    public PublicDataRecordController(PublicDataRecordQueryService publicDataRecordQueryService) {
        this.publicDataRecordQueryService = publicDataRecordQueryService;
    }

    @GetMapping
    @Operation(summary = "공공데이터 레코드 목록 조회", description = "소스 타입 기준으로 저장된 공공데이터 레코드를 페이지 단위로 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"page\":0,\"size\":20,\"totalElements\":2,\"totalPages\":1,\"records\":[{\"id\":9001,\"sourceType\":\"KEPAD_RECRUITMENT\",\"externalId\":\"KEPAD-20260508-0001\",\"rawFetchedAt\":\"2026-05-08T16:30:00+09:00\",\"updatedAt\":\"2026-05-08T16:31:00+09:00\",\"fields\":{\"jobNm\":\"사무보조\"},\"payloadJson\":null}]}"
            )))
    public ResponseEntity<PublicDataRecordPageResponseDto> getRecords(
            @RequestParam(name = "sourceType") PublicDataSourceType sourceType,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(name = "includePayload", defaultValue = "false") boolean includePayload
    ) {
        return ResponseEntity.ok(publicDataRecordQueryService.getRecords(sourceType, page, size, includePayload));
    }

    @GetMapping("/{recordId}")
    @Operation(summary = "공공데이터 레코드 단건 조회", description = "레코드 ID 기준으로 저장된 공공데이터 상세를 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"id\":9001,\"sourceType\":\"KEPAD_RECRUITMENT\",\"externalId\":\"KEPAD-20260508-0001\",\"rawFetchedAt\":\"2026-05-08T16:30:00+09:00\",\"updatedAt\":\"2026-05-08T16:31:00+09:00\",\"fields\":{\"jobNm\":\"사무보조\"},\"payloadJson\":\"{\\\"jobNm\\\":\\\"사무보조\\\"}\"}"
            )))
    public ResponseEntity<PublicDataRecordResponseDto> getRecord(
            @PathVariable("recordId") Long recordId,
            @RequestParam(name = "includePayload", defaultValue = "true") boolean includePayload
    ) {
        return ResponseEntity.ok(publicDataRecordQueryService.getRecord(recordId, includePayload));
    }
}
