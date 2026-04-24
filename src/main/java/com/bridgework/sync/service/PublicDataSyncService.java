package com.bridgework.sync.service;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.dto.PublicDataApiItemDto;
import com.bridgework.sync.dto.PublicDataApiPageResponseDto;
import com.bridgework.sync.dto.SourceConfigResponseDto;
import com.bridgework.sync.dto.SourceSyncResultDto;
import com.bridgework.sync.dto.SyncLogResponseDto;
import com.bridgework.sync.dto.SyncRunResponseDto;
import com.bridgework.sync.entity.PublicDataRecord;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.PublicDataSyncLog;
import com.bridgework.sync.entity.SyncRequestSource;
import com.bridgework.sync.entity.SyncStatus;
import com.bridgework.sync.exception.SyncSourceDisabledException;
import com.bridgework.sync.exception.SyncSourceNotFoundException;
import com.bridgework.sync.repository.PublicDataRecordRepository;
import com.bridgework.sync.repository.PublicDataSyncLogRepository;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PublicDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(PublicDataSyncService.class);

    private final BridgeWorkSyncProperties syncProperties;
    private final PublicDataApiClient publicDataApiClient;
    private final PublicDataRecordRepository publicDataRecordRepository;
    private final PublicDataRecordFieldService publicDataRecordFieldService;
    private final PublicDataSyncLogRepository publicDataSyncLogRepository;

    public PublicDataSyncService(BridgeWorkSyncProperties syncProperties,
                                 PublicDataApiClient publicDataApiClient,
                                 PublicDataRecordRepository publicDataRecordRepository,
                                 PublicDataRecordFieldService publicDataRecordFieldService,
                                 PublicDataSyncLogRepository publicDataSyncLogRepository) {
        this.syncProperties = syncProperties;
        this.publicDataApiClient = publicDataApiClient;
        this.publicDataRecordRepository = publicDataRecordRepository;
        this.publicDataRecordFieldService = publicDataRecordFieldService;
        this.publicDataSyncLogRepository = publicDataSyncLogRepository;
    }

    @PostConstruct
    void validateConfiguration() {
        EnumSet<PublicDataSourceType> configuredSources = EnumSet.noneOf(PublicDataSourceType.class);

        for (BridgeWorkSyncProperties.SourceConfig sourceConfig : syncProperties.getSources()) {
            if (!configuredSources.add(sourceConfig.getSourceType())) {
                throw new IllegalStateException("중복 동기화 소스 설정: " + sourceConfig.getSourceType());
            }

            if (sourceConfig.isEnabled() && (sourceConfig.getServiceKey() == null || sourceConfig.getServiceKey().isBlank())) {
                throw new IllegalStateException("활성화된 소스의 serviceKey가 비어 있습니다: " + sourceConfig.getSourceType());
            }

            if (sourceConfig.isEnabled()
                    && (sourceConfig.getSourceType() == PublicDataSourceType.RAIL_WHEELCHAIR_LIFT
                    || sourceConfig.getSourceType() == PublicDataSourceType.RAIL_WHEELCHAIR_LIFT_MOVEMENT
                    || sourceConfig.getSourceType() == PublicDataSourceType.SEOUL_WHEELCHAIR_LIFT)
                    && (syncProperties.getKricStationCodeFilePath() == null
                    || syncProperties.getKricStationCodeFilePath().isBlank())) {
                throw new IllegalStateException(
                        "RAIL_WHEELCHAIR_LIFT/RAIL_WHEELCHAIR_LIFT_MOVEMENT/SEOUL_WHEELCHAIR_LIFT 활성화 시 "
                                + "kricStationCodeFilePath가 필요합니다."
                );
            }
        }
    }

    public SyncRunResponseDto syncAll(SyncRequestSource requestSource) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        List<SourceSyncResultDto> results = new ArrayList<>();

        for (BridgeWorkSyncProperties.SourceConfig sourceConfig : syncProperties.getSources()) {
            if (!sourceConfig.isEnabled()) {
                continue;
            }
            results.add(syncSourceInternal(sourceConfig, requestSource));
        }

        return buildSummary(startedAt, results);
    }

    public SyncRunResponseDto syncSingle(PublicDataSourceType sourceType, SyncRequestSource requestSource) {
        BridgeWorkSyncProperties.SourceConfig sourceConfig = findSourceConfig(sourceType);
        if (!sourceConfig.isEnabled()) {
            throw new SyncSourceDisabledException(sourceType);
        }

        OffsetDateTime startedAt = OffsetDateTime.now();
        List<SourceSyncResultDto> results = List.of(syncSourceInternal(sourceConfig, requestSource));
        return buildSummary(startedAt, results);
    }

    public List<SyncLogResponseDto> getRecentLogs(PublicDataSourceType sourceType) {
        List<PublicDataSyncLog> logs = sourceType == null
                ? publicDataSyncLogRepository.findTop20ByOrderByStartedAtDesc()
                : publicDataSyncLogRepository.findTop20BySourceTypeOrderByStartedAtDesc(sourceType);

        return logs.stream()
                .map(logItem -> new SyncLogResponseDto(
                        logItem.getId(),
                        logItem.getSourceType(),
                        logItem.getRequestSource(),
                        logItem.getStatus(),
                        logItem.getProcessedCount(),
                        logItem.getNewCount(),
                        logItem.getUpdatedCount(),
                        logItem.getFailedCount(),
                        logItem.getErrorMessage(),
                        logItem.getStartedAt(),
                        logItem.getEndedAt()
                ))
                .toList();
    }

    public List<SourceConfigResponseDto> getSourceConfigs() {
        return syncProperties.getSources().stream()
                .map(source -> new SourceConfigResponseDto(
                        source.getSourceType(),
                        source.isEnabled(),
                        source.getBaseUrl(),
                        source.getPageSize(),
                        source.getMaxPages()
                ))
                .toList();
    }

    private SourceSyncResultDto syncSourceInternal(BridgeWorkSyncProperties.SourceConfig sourceConfig,
                                                   SyncRequestSource requestSource) {
        PublicDataSourceType sourceType = sourceConfig.getSourceType();
        PublicDataSyncLog syncLog = createSyncLog(sourceType, requestSource);

        int processedCount = 0;
        int newCount = 0;
        int updatedCount = 0;
        int failedCount = 0;
        int deletedCount = 0;
        SyncStatus syncStatus = SyncStatus.SUCCESS;
        String message = "동기화 완료";
        Set<String> fetchedExternalIds = new HashSet<>();

        try {
            for (int pageNo = 1; pageNo <= sourceConfig.getMaxPages(); pageNo++) {
                PublicDataApiPageResponseDto pageResponse = publicDataApiClient.fetchPage(sourceConfig, pageNo);

                if (pageResponse.items().isEmpty()) {
                    break;
                }

                for (PublicDataApiItemDto item : pageResponse.items()) {
                    // 동일 호출 내 중복 응답은 마지막 상태와 무관하게 1건으로 처리한다.
                    if (!fetchedExternalIds.add(item.externalId())) {
                        continue;
                    }

                    processedCount++;
                    try {
                        UpsertResult upsertResult = upsertRecord(sourceType, item);
                        if (upsertResult == UpsertResult.INSERTED) {
                            newCount++;
                        } else if (upsertResult == UpsertResult.UPDATED) {
                            updatedCount++;
                        }
                    } catch (Exception exception) {
                        failedCount++;
                        log.warn("데이터 저장 실패 source={} externalId={} reason={}",
                                sourceType,
                                item.externalId(),
                                exception.getMessage());
                    }
                }

                if (!pageResponse.hasNext()) {
                    break;
                }
            }

            if (failedCount == 0) {
                deletedCount = removeDeletedRecords(sourceType, fetchedExternalIds);
                if (deletedCount > 0) {
                    message = "동기화 완료 (삭제 " + deletedCount + "건)";
                }
            }

            if (failedCount > 0) {
                syncStatus = SyncStatus.PARTIAL_SUCCESS;
                message = "일부 데이터 저장 실패";
            }

            if (failedCount > 0 && deletedCount == 0) {
                log.warn("부분 실패로 삭제 동기화를 건너뜀 source={} failedCount={}", sourceType, failedCount);
            }
        } catch (Exception exception) {
            failedCount++;
            syncStatus = processedCount > 0 ? SyncStatus.PARTIAL_SUCCESS : SyncStatus.FAILED;
            message = exception.getMessage();
            log.error("동기화 실패 source={} reason={}", sourceType, exception.getMessage(), exception);
        }

        finishSyncLog(syncLog, syncStatus, processedCount, newCount, updatedCount, failedCount, message);
        return new SourceSyncResultDto(sourceType, syncStatus, processedCount, newCount, updatedCount, failedCount, message);
    }

    private int removeDeletedRecords(PublicDataSourceType sourceType, Set<String> fetchedExternalIds) {
        List<PublicDataRecord> existingRecords = publicDataRecordRepository.findBySourceType(sourceType);
        List<PublicDataRecord> recordsToDelete = new ArrayList<>();

        for (PublicDataRecord existingRecord : existingRecords) {
            if (!fetchedExternalIds.contains(existingRecord.getExternalId())) {
                recordsToDelete.add(existingRecord);
            }
        }

        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        publicDataRecordRepository.deleteAllInBatch(recordsToDelete);
        return recordsToDelete.size();
    }

    private UpsertResult upsertRecord(PublicDataSourceType sourceType, PublicDataApiItemDto item) {
        Optional<PublicDataRecord> existingRecord = publicDataRecordRepository
                .findBySourceTypeAndExternalId(sourceType, item.externalId());

        OffsetDateTime now = OffsetDateTime.now();

        if (existingRecord.isEmpty()) {
            PublicDataRecord record = new PublicDataRecord();
            record.setSourceType(sourceType);
            record.setExternalId(item.externalId());
            record.setPayloadJson(item.payloadJson());
            record.setPayloadHash(item.payloadHash());
            record.setRawFetchedAt(now);
            PublicDataRecord savedRecord = publicDataRecordRepository.save(record);
            publicDataRecordFieldService.replaceFields(savedRecord);
            return UpsertResult.INSERTED;
        }

        PublicDataRecord record = existingRecord.get();
        record.setRawFetchedAt(now);

        // 동일 키 데이터는 해시를 비교해 변경 건만 업데이트한다.
        if (!record.getPayloadHash().equals(item.payloadHash())) {
            record.setPayloadJson(item.payloadJson());
            record.setPayloadHash(item.payloadHash());
            PublicDataRecord savedRecord = publicDataRecordRepository.save(record);
            publicDataRecordFieldService.replaceFields(savedRecord);
            return UpsertResult.UPDATED;
        }

        publicDataRecordRepository.save(record);
        return UpsertResult.UNCHANGED;
    }

    private PublicDataSyncLog createSyncLog(PublicDataSourceType sourceType, SyncRequestSource requestSource) {
        PublicDataSyncLog syncLog = new PublicDataSyncLog();
        syncLog.setSourceType(sourceType);
        syncLog.setRequestSource(requestSource);
        syncLog.setStatus(SyncStatus.SUCCESS);
        return publicDataSyncLogRepository.save(syncLog);
    }

    private void finishSyncLog(PublicDataSyncLog syncLog,
                               SyncStatus syncStatus,
                               int processedCount,
                               int newCount,
                               int updatedCount,
                               int failedCount,
                               String message) {
        syncLog.setStatus(syncStatus);
        syncLog.setProcessedCount(processedCount);
        syncLog.setNewCount(newCount);
        syncLog.setUpdatedCount(updatedCount);
        syncLog.setFailedCount(failedCount);
        syncLog.setErrorMessage(syncStatus == SyncStatus.SUCCESS ? null : truncateMessage(message));
        syncLog.setEndedAt(OffsetDateTime.now());
        publicDataSyncLogRepository.save(syncLog);
    }

    private String truncateMessage(String message) {
        if (message == null || message.isBlank()) {
            return "오류 메시지가 비어 있습니다.";
        }

        if (message.length() <= 500) {
            return message;
        }

        return message.substring(0, 500);
    }

    private BridgeWorkSyncProperties.SourceConfig findSourceConfig(PublicDataSourceType sourceType) {
        return syncProperties.getSources().stream()
                .filter(source -> source.getSourceType() == sourceType)
                .findFirst()
                .orElseThrow(() -> new SyncSourceNotFoundException(sourceType));
    }

    private SyncRunResponseDto buildSummary(OffsetDateTime startedAt, List<SourceSyncResultDto> results) {
        int processed = results.stream().mapToInt(SourceSyncResultDto::processedCount).sum();
        int newCount = results.stream().mapToInt(SourceSyncResultDto::newCount).sum();
        int updated = results.stream().mapToInt(SourceSyncResultDto::updatedCount).sum();
        int failed = results.stream().mapToInt(SourceSyncResultDto::failedCount).sum();

        return new SyncRunResponseDto(
                startedAt,
                OffsetDateTime.now(),
                results,
                processed,
                newCount,
                updated,
                failed
        );
    }

    private enum UpsertResult {
        INSERTED,
        UPDATED,
        UNCHANGED
    }
}
