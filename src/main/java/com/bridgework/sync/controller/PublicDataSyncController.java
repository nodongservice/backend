package com.bridgework.sync.controller;

import com.bridgework.sync.dto.SourceConfigResponseDto;
import com.bridgework.sync.dto.NormalizedCountSummaryResponseDto;
import com.bridgework.sync.dto.SyncLogResponseDto;
import com.bridgework.sync.dto.SyncLogResetResponseDto;
import com.bridgework.sync.dto.SyncRunAcceptedResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.SyncRequestSource;
import com.bridgework.sync.service.PublicDataSyncExecutionLockService;
import com.bridgework.sync.service.PublicDataSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final PublicDataSyncExecutionLockService publicDataSyncExecutionLockService;
    private final Executor syncTaskExecutor;

    public PublicDataSyncController(PublicDataSyncService publicDataSyncService,
                                    PublicDataSyncExecutionLockService publicDataSyncExecutionLockService,
                                    @Qualifier("syncTaskExecutor") Executor syncTaskExecutor) {
        this.publicDataSyncService = publicDataSyncService;
        this.publicDataSyncExecutionLockService = publicDataSyncExecutionLockService;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    @PostMapping("/run")
    @Operation(summary = "공공데이터 수동 동기화 실행", description = "전체 또는 특정 소스의 최신 데이터를 수집하고 DB를 동기화한다.")
    public ResponseEntity<SyncRunAcceptedResponseDto> runSync(
            @RequestParam(name = "sourceType", required = false) PublicDataSourceType sourceType
    ) {
        publicDataSyncExecutionLockService.runManualAsyncOrThrow(syncTaskExecutor, () -> {
            if (sourceType == null) {
                publicDataSyncService.syncAll(SyncRequestSource.MANUAL);
            } else {
                publicDataSyncService.syncSingle(sourceType, SyncRequestSource.MANUAL);
            }
        });

        return ResponseEntity.accepted().body(new SyncRunAcceptedResponseDto(
                OffsetDateTime.now(),
                sourceType,
                "동기화 요청이 접수되었습니다. 최종 결과는 Discord 알림으로 전달됩니다."
        ));
    }

    @GetMapping("/logs")
    @Operation(summary = "동기화 실행 로그 조회", description = "최근 동기화 실행 결과를 조회한다. sourceType 지정 시 해당 소스 로그만 반환한다.")
    public ResponseEntity<List<SyncLogResponseDto>> getRecentLogs(
            @RequestParam(name = "sourceType", required = false) PublicDataSourceType sourceType
    ) {
        return ResponseEntity.ok(publicDataSyncService.getRecentLogs(sourceType));
    }

    @DeleteMapping("/logs")
    @Operation(summary = "동기화 실행 로그 초기화", description = "저장된 동기화 실행 로그를 모두 삭제한다.")
    public ResponseEntity<SyncLogResetResponseDto> resetSyncLogs() {
        long deletedCount = publicDataSyncService.resetSyncLogs();
        return ResponseEntity.ok(new SyncLogResetResponseDto(deletedCount));
    }

    @GetMapping("/sources")
    @Operation(summary = "동기화 소스 설정 조회", description = "서버에 등록된 공공데이터 소스별 동기화 설정을 조회한다.")
    public ResponseEntity<List<SourceConfigResponseDto>> getSources() {
        return ResponseEntity.ok(publicDataSyncService.getSourceConfigs());
    }

    @GetMapping("/normalized-counts")
    @Operation(summary = "정규화 테이블 건수 조회", description = "모든 공공데이터 정규화 테이블(pd_*)의 소스별/전체 건수를 조회한다.")
    public ResponseEntity<NormalizedCountSummaryResponseDto> getNormalizedCounts() {
        return ResponseEntity.ok(publicDataSyncService.getNormalizedCounts());
    }
}
