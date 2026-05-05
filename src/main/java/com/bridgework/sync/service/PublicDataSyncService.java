package com.bridgework.sync.service;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.dto.PublicDataApiItemDto;
import com.bridgework.sync.dto.PublicDataApiPageResponseDto;
import com.bridgework.sync.dto.SourceLatestRevisionDto;
import com.bridgework.sync.dto.SourceConfigResponseDto;
import com.bridgework.sync.dto.SourceSyncResultDto;
import com.bridgework.sync.dto.SyncLogResponseDto;
import com.bridgework.sync.dto.SyncRunResponseDto;
import com.bridgework.sync.entity.PublicDataRecord;
import com.bridgework.sync.entity.PublicDataSourceSnapshot;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.PublicDataSyncLog;
import com.bridgework.sync.entity.SyncRequestSource;
import com.bridgework.sync.entity.SyncStatus;
import com.bridgework.sync.exception.SyncSourceDisabledException;
import com.bridgework.sync.exception.SyncSourceNotFoundException;
import com.bridgework.sync.repository.PublicDataRecordRepository;
import com.bridgework.sync.repository.PublicDataSourceSnapshotRepository;
import com.bridgework.sync.repository.PublicDataSyncLogRepository;
import com.bridgework.sync.normalized.PublicDataNormalizedStoreService;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(PublicDataSyncService.class);
    private static final int DELETE_BATCH_SIZE = 500;

    private final BridgeWorkSyncProperties syncProperties;
    private final PublicDataApiClient publicDataApiClient;
    private final PublicDataRecordRepository publicDataRecordRepository;
    private final PublicDataRecordFieldService publicDataRecordFieldService;
    private final PublicDataSyncLogRepository publicDataSyncLogRepository;
    private final PublicDataSourceSnapshotRepository publicDataSourceSnapshotRepository;
    private final PublicDataNormalizedStoreService publicDataNormalizedStoreService;

    public PublicDataSyncService(BridgeWorkSyncProperties syncProperties,
                                 PublicDataApiClient publicDataApiClient,
                                 PublicDataRecordRepository publicDataRecordRepository,
                                 PublicDataRecordFieldService publicDataRecordFieldService,
                                 PublicDataSyncLogRepository publicDataSyncLogRepository,
                                 PublicDataSourceSnapshotRepository publicDataSourceSnapshotRepository,
                                 PublicDataNormalizedStoreService publicDataNormalizedStoreService) {
        this.syncProperties = syncProperties;
        this.publicDataApiClient = publicDataApiClient;
        this.publicDataRecordRepository = publicDataRecordRepository;
        this.publicDataRecordFieldService = publicDataRecordFieldService;
        this.publicDataSyncLogRepository = publicDataSyncLogRepository;
        this.publicDataSourceSnapshotRepository = publicDataSourceSnapshotRepository;
        this.publicDataNormalizedStoreService = publicDataNormalizedStoreService;
    }

    @PostConstruct
    void validateConfiguration() {
        EnumSet<PublicDataSourceType> configuredSources = EnumSet.noneOf(PublicDataSourceType.class);

        for (BridgeWorkSyncProperties.SourceConfig sourceConfig : syncProperties.getSources()) {
            if (!configuredSources.add(sourceConfig.getSourceType())) {
                throw new IllegalStateException("중복 동기화 소스 설정: " + sourceConfig.getSourceType());
            }

            if (sourceConfig.isEnabled()
                    && isServiceKeyRequired(sourceConfig.getSourceType())
                    && (sourceConfig.getServiceKey() == null || sourceConfig.getServiceKey().isBlank())) {
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

            if (sourceConfig.isEnabled()
                    && (sourceConfig.getSourceType() == PublicDataSourceType.KEPAD_RECRUITMENT
                    || sourceConfig.getSourceType() == PublicDataSourceType.KEPAD_SUPPORT_AGENCY)
                    && (syncProperties.getNaverGeocodeApiKeyId() == null
                    || syncProperties.getNaverGeocodeApiKeyId().isBlank()
                    || syncProperties.getNaverGeocodeApiKey() == null
                    || syncProperties.getNaverGeocodeApiKey().isBlank())) {
                throw new IllegalStateException(
                        "KEPAD_RECRUITMENT/KEPAD_SUPPORT_AGENCY 활성화 시 naverGeocodeApiKeyId/naverGeocodeApiKey가 필요합니다."
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

    @Transactional
    public long resetSyncLogs() {
        long deletedCount = publicDataSyncLogRepository.count();
        publicDataSyncLogRepository.deleteAllInBatch();
        return deletedCount;
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
        Map<String, Integer> failureReasonCounts = new LinkedHashMap<>();
        SourceLatestRevisionDto latestRevisionDto = null;

        try {
            latestRevisionDto = publicDataApiClient.fetchLatestRevision(sourceConfig).orElse(null);
            if (latestRevisionDto != null && isUnchangedLatestRevision(sourceType, latestRevisionDto.revisionKey())) {
                syncStatus = SyncStatus.SKIP;
                message = "최신 파일 수정일 동일로 동기화 스킵";
                finishSyncLog(syncLog, syncStatus, 0, 0, 0, 0, message);
                return new SourceSyncResultDto(sourceType, syncStatus, 0, 0, 0, 0, message);
            }

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
                        OffsetDateTime fetchedAt = OffsetDateTime.now();
                        UpsertResult upsertResult = upsertRecord(sourceType, item);
                        // 변경건만 정규화 재계산하고, 미변경건은 수집시각만 갱신한다.
                        if (upsertResult == UpsertResult.UNCHANGED) {
                            publicDataNormalizedStoreService.touch(sourceType, item.externalId(), fetchedAt);
                        } else {
                            publicDataNormalizedStoreService.upsert(sourceType, item, fetchedAt);
                        }
                        if (upsertResult == UpsertResult.INSERTED) {
                            newCount++;
                        } else if (upsertResult == UpsertResult.UPDATED) {
                            updatedCount++;
                        }
                    } catch (Exception exception) {
                        // 지오코딩 실패는 데이터 품질 오류로 간주하여 소스 동기화를 즉시 실패 처리한다.
                        if (isGeocodingFailure(exception)) {
                            throw exception;
                        }
                        failedCount++;
                        String failureReason = summarizeFailureReason(exception);
                        failureReasonCounts.merge(failureReason, 1, Integer::sum);
                        log.warn("데이터 저장 실패 source={} externalId={} reason={}",
                                sourceType,
                                item.externalId(),
                                failureReason);
                    }
                }

                if (!pageResponse.hasNext()) {
                    break;
                }
            }

            if (failedCount == 0) {
                deletedCount = removeDeletedRecords(sourceType, fetchedExternalIds);
                publicDataNormalizedStoreService.deleteMissing(sourceType, fetchedExternalIds);
                if (deletedCount > 0) {
                    message = "동기화 완료 (삭제 " + deletedCount + "건)";
                }
            }

            if (failedCount > 0) {
                syncStatus = SyncStatus.PARTIAL_SUCCESS;
                message = buildFailureSummaryMessage("일부 데이터 저장 실패", failureReasonCounts);
            }

            if (failedCount > 0 && deletedCount == 0) {
                log.warn("부분 실패로 삭제 동기화를 건너뜀 source={} failedCount={}", sourceType, failedCount);
            } else if (failedCount == 0 && latestRevisionDto != null) {
                upsertSourceSnapshot(sourceType, latestRevisionDto);
            }
        } catch (Exception exception) {
            failedCount++;
            if (isGeocodingFailure(exception)) {
                syncStatus = SyncStatus.FAILED;
            } else {
                syncStatus = processedCount > 0 ? SyncStatus.PARTIAL_SUCCESS : SyncStatus.FAILED;
            }
            message = exception.getMessage();
            log.error("동기화 실패 source={} reason={}", sourceType, exception.getMessage(), exception);
        }

        finishSyncLog(syncLog, syncStatus, processedCount, newCount, updatedCount, failedCount, message);
        return new SourceSyncResultDto(sourceType, syncStatus, processedCount, newCount, updatedCount, failedCount, message);
    }

    private String summarizeFailureReason(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }

        String reasonText = rootCause.getMessage();
        if (reasonText == null || reasonText.isBlank()) {
            reasonText = exception.getMessage();
        }
        if (reasonText == null || reasonText.isBlank()) {
            reasonText = exception.getClass().getSimpleName();
        }

        String normalized = reasonText.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120) + "...";
        }
        return normalized;
    }

    private boolean isGeocodingFailure(Exception exception) {
        String reason = summarizeFailureReason(exception);
        return reason != null && reason.startsWith("지오코딩 실패:");
    }

    private String buildFailureSummaryMessage(String baseMessage, Map<String, Integer> failureReasonCounts) {
        if (failureReasonCounts == null || failureReasonCounts.isEmpty()) {
            return baseMessage;
        }

        List<Map.Entry<String, Integer>> sortedReasons = failureReasonCounts.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .toList();

        List<String> topReasons = new ArrayList<>();
        int maxReasons = Math.min(3, sortedReasons.size());
        for (int index = 0; index < maxReasons; index++) {
            Map.Entry<String, Integer> entry = sortedReasons.get(index);
            topReasons.add(entry.getValue() + "건: " + entry.getKey());
        }

        return baseMessage + " | 원인요약 " + String.join(" / ", topReasons);
    }

    private boolean isUnchangedLatestRevision(PublicDataSourceType sourceType, String revisionKey) {
        return publicDataSourceSnapshotRepository.findById(sourceType)
                .map(snapshot -> snapshot.getLatestRevision().equals(revisionKey))
                .orElse(false);
    }

    private void upsertSourceSnapshot(PublicDataSourceType sourceType, SourceLatestRevisionDto latestRevisionDto) {
        PublicDataSourceSnapshot snapshot = publicDataSourceSnapshotRepository.findById(sourceType)
                .orElseGet(() -> {
                    PublicDataSourceSnapshot newSnapshot = new PublicDataSourceSnapshot();
                    newSnapshot.setSourceType(sourceType);
                    return newSnapshot;
                });

        snapshot.setLatestRevision(latestRevisionDto.revisionKey());
        snapshot.setLatestFileName(latestRevisionDto.fileName());
        snapshot.setLatestModifiedDate(latestRevisionDto.modifiedDate());
        publicDataSourceSnapshotRepository.save(snapshot);
    }

    private int removeDeletedRecords(PublicDataSourceType sourceType, Set<String> fetchedExternalIds) {
        List<PublicDataRecordRepository.RecordIdentityView> existingRecords =
                publicDataRecordRepository.findRecordIdentityBySourceType(sourceType);
        List<Long> idsToDelete = new ArrayList<>();

        for (PublicDataRecordRepository.RecordIdentityView existingRecord : existingRecords) {
            if (!fetchedExternalIds.contains(existingRecord.getExternalId())) {
                idsToDelete.add(existingRecord.getId());
            }
        }

        if (idsToDelete.isEmpty()) {
            return 0;
        }

        // 대량 삭제를 청크로 분할해 HQL 파서 StackOverflow를 방지한다.
        for (int start = 0; start < idsToDelete.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, idsToDelete.size());
            List<Long> chunk = idsToDelete.subList(start, end);
            publicDataRecordRepository.deleteAllByIdInNative(chunk);
        }
        return idsToDelete.size();
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
        syncLog.setErrorMessage((syncStatus == SyncStatus.SUCCESS || syncStatus == SyncStatus.SKIP)
                ? null
                : truncateMessage(message));
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

    private boolean isServiceKeyRequired(PublicDataSourceType sourceType) {
        return sourceType != PublicDataSourceType.SEOUL_WHEELCHAIR_RAMP_STATUS
                && sourceType != PublicDataSourceType.SEOUL_LOW_FLOOR_BUS_ROUTE_RETENTION;
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
