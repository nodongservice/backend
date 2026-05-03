package com.bridgework.sync.controller;

import com.bridgework.sync.dto.SourceConfigResponseDto;
import com.bridgework.sync.dto.SyncLogResponseDto;
import com.bridgework.sync.dto.SyncRunResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.SyncRequestSource;
import com.bridgework.sync.service.PublicDataSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync/public-data")
@Tag(name = "PublicDataSync", description = "공공데이터 동기화 실행/로그/설정 조회 API")
public class PublicDataSyncController {

    private final PublicDataSyncService publicDataSyncService;

    public PublicDataSyncController(PublicDataSyncService publicDataSyncService) {
        this.publicDataSyncService = publicDataSyncService;
    }

    @PostMapping("/run")
    @Operation(summary = "공공데이터 수동 동기화 실행", description = "전체 또는 특정 소스의 최신 데이터를 수집하고 DB를 동기화한다.")
    public ResponseEntity<SyncRunResponseDto> runSync(
            @RequestParam(name = "sourceType", required = false) PublicDataSourceType sourceType
    ) {
        SyncRunResponseDto response = sourceType == null
                ? publicDataSyncService.syncAll(SyncRequestSource.MANUAL)
                : publicDataSyncService.syncSingle(sourceType, SyncRequestSource.MANUAL);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/logs")
    @Operation(summary = "동기화 실행 로그 조회", description = "최근 동기화 실행 결과를 조회한다. sourceType 지정 시 해당 소스 로그만 반환한다.")
    public ResponseEntity<List<SyncLogResponseDto>> getRecentLogs(
            @RequestParam(name = "sourceType", required = false) PublicDataSourceType sourceType
    ) {
        return ResponseEntity.ok(publicDataSyncService.getRecentLogs(sourceType));
    }

    @GetMapping("/sources")
    @Operation(summary = "동기화 소스 설정 조회", description = "서버에 등록된 공공데이터 소스별 동기화 설정을 조회한다.")
    public ResponseEntity<List<SourceConfigResponseDto>> getSources() {
        return ResponseEntity.ok(publicDataSyncService.getSourceConfigs());
    }
}
