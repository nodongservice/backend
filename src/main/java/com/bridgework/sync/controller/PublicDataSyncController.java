package com.bridgework.sync.controller;

import com.bridgework.sync.dto.SourceConfigResponseDto;
import com.bridgework.sync.dto.SyncLogResponseDto;
import com.bridgework.sync.dto.SyncRunResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.SyncRequestSource;
import com.bridgework.sync.service.PublicDataSyncService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync/public-data")
public class PublicDataSyncController {

    private final PublicDataSyncService publicDataSyncService;

    public PublicDataSyncController(PublicDataSyncService publicDataSyncService) {
        this.publicDataSyncService = publicDataSyncService;
    }

    @PostMapping("/run")
    public ResponseEntity<SyncRunResponseDto> runSync(
            @RequestParam(name = "sourceType", required = false) PublicDataSourceType sourceType
    ) {
        SyncRunResponseDto response = sourceType == null
                ? publicDataSyncService.syncAll(SyncRequestSource.MANUAL)
                : publicDataSyncService.syncSingle(sourceType, SyncRequestSource.MANUAL);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/logs")
    public ResponseEntity<List<SyncLogResponseDto>> getRecentLogs(
            @RequestParam(name = "sourceType", required = false) PublicDataSourceType sourceType
    ) {
        return ResponseEntity.ok(publicDataSyncService.getRecentLogs(sourceType));
    }

    @GetMapping("/sources")
    public ResponseEntity<List<SourceConfigResponseDto>> getSources() {
        return ResponseEntity.ok(publicDataSyncService.getSourceConfigs());
    }
}
