package com.bridgework.sync.controller;

import com.bridgework.sync.dto.PublicDataRecordPageResponseDto;
import com.bridgework.sync.dto.PublicDataRecordResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.service.PublicDataRecordQueryService;
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
@RequestMapping("/api/v1/public-data/records")
public class PublicDataRecordController {

    private final PublicDataRecordQueryService publicDataRecordQueryService;

    public PublicDataRecordController(PublicDataRecordQueryService publicDataRecordQueryService) {
        this.publicDataRecordQueryService = publicDataRecordQueryService;
    }

    @GetMapping
    public ResponseEntity<PublicDataRecordPageResponseDto> getRecords(
            @RequestParam(name = "sourceType") PublicDataSourceType sourceType,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(name = "includePayload", defaultValue = "false") boolean includePayload
    ) {
        return ResponseEntity.ok(publicDataRecordQueryService.getRecords(sourceType, page, size, includePayload));
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<PublicDataRecordResponseDto> getRecord(
            @PathVariable("recordId") Long recordId,
            @RequestParam(name = "includePayload", defaultValue = "true") boolean includePayload
    ) {
        return ResponseEntity.ok(publicDataRecordQueryService.getRecord(recordId, includePayload));
    }
}
