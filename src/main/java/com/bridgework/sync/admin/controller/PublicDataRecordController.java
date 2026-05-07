package com.bridgework.sync.admin.controller;

import com.bridgework.sync.dto.PublicDataRecordPageResponseDto;
import com.bridgework.sync.dto.PublicDataRecordResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.service.PublicDataRecordQueryService;
import io.swagger.v3.oas.annotations.Operation;
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
public class PublicDataRecordController {

    private final PublicDataRecordQueryService publicDataRecordQueryService;

    public PublicDataRecordController(PublicDataRecordQueryService publicDataRecordQueryService) {
        this.publicDataRecordQueryService = publicDataRecordQueryService;
    }

    @GetMapping
    @Operation(summary = "공공데이터 레코드 목록 조회", description = "소스 타입 기준으로 저장된 공공데이터 레코드를 페이지 단위로 조회한다.")
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
    public ResponseEntity<PublicDataRecordResponseDto> getRecord(
            @PathVariable("recordId") Long recordId,
            @RequestParam(name = "includePayload", defaultValue = "true") boolean includePayload
    ) {
        return ResponseEntity.ok(publicDataRecordQueryService.getRecord(recordId, includePayload));
    }
}
