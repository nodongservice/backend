package com.bridgework.sync.service;

import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.common.notification.DiscordNotifierService;
import com.bridgework.sync.dto.PublicDataApiItemDto;
import com.bridgework.sync.dto.PublicDataApiPageResponseDto;
import com.bridgework.sync.dto.NormalizedCountSummaryResponseDto;
import com.bridgework.sync.dto.NormalizedSourceCountResponseDto;
import com.bridgework.sync.dto.SourceLatestRevisionDto;
import com.bridgework.sync.dto.SourceConfigResponseDto;
import com.bridgework.sync.dto.SourceSyncResultDto;
import com.bridgework.sync.dto.SyncLogResponseDto;
import com.bridgework.sync.dto.SyncRunResponseDto;
import com.bridgework.sync.entity.PublicDataRecord;
import com.bridgework.sync.entity.PublicDataSourceSnapshot;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.PublicDataSyncLog;
import com.bridgework.sync.entity.RecordSyncStatus;
import com.bridgework.sync.entity.SyncRequestSource;
import com.bridgework.sync.entity.SyncStatus;
import com.bridgework.sync.exception.SyncSourceDisabledException;
import com.bridgework.sync.exception.SyncSourceNotFoundException;
import com.bridgework.sync.repository.PublicDataRecordRepository;
import com.bridgework.sync.repository.PublicDataSourceSnapshotRepository;
import com.bridgework.sync.repository.PublicDataSyncLogRepository;
import com.bridgework.sync.normalized.PublicDataNormalizedStoreService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(PublicDataSyncService.class);
    private static final int DELETE_BATCH_SIZE = 500;
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern COMPACT_DATE_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(\\d{2})(\\d{2})(?!\\d)");
    private static final Pattern SEPARATED_DATE_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})(?!\\d)");

    private final BridgeWorkSyncProperties syncProperties;
    private final PublicDataApiClient publicDataApiClient;
    private final PublicDataRecordRepository publicDataRecordRepository;
    private final PublicDataRecordFieldService publicDataRecordFieldService;
    private final PublicDataSyncLogRepository publicDataSyncLogRepository;
    private final PublicDataSourceSnapshotRepository publicDataSourceSnapshotRepository;
    private final PublicDataNormalizedStoreService publicDataNormalizedStoreService;
    private final DiscordNotifierService discordNotifierService;
    private final ObjectMapper objectMapper;

    public PublicDataSyncService(BridgeWorkSyncProperties syncProperties,
                                 PublicDataApiClient publicDataApiClient,
                                 PublicDataRecordRepository publicDataRecordRepository,
                                 PublicDataRecordFieldService publicDataRecordFieldService,
                                 PublicDataSyncLogRepository publicDataSyncLogRepository,
                                 PublicDataSourceSnapshotRepository publicDataSourceSnapshotRepository,
                                 PublicDataNormalizedStoreService publicDataNormalizedStoreService,
                                 DiscordNotifierService discordNotifierService,
                                 ObjectMapper objectMapper) {
        this.syncProperties = syncProperties;
        this.publicDataApiClient = publicDataApiClient;
        this.publicDataRecordRepository = publicDataRecordRepository;
        this.publicDataRecordFieldService = publicDataRecordFieldService;
        this.publicDataSyncLogRepository = publicDataSyncLogRepository;
        this.publicDataSourceSnapshotRepository = publicDataSourceSnapshotRepository;
        this.publicDataNormalizedStoreService = publicDataNormalizedStoreService;
        this.discordNotifierService = discordNotifierService;
        this.objectMapper = objectMapper;
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
        discordNotifierService.notifySyncStarted(requestSource, null, startedAt);
        List<SourceSyncResultDto> results = new ArrayList<>();
        Map<PublicDataSourceType, Duration> sourceDurations = new LinkedHashMap<>();

        for (BridgeWorkSyncProperties.SourceConfig sourceConfig : syncProperties.getSources()) {
            if (!sourceConfig.isEnabled()) {
                continue;
            }
            OffsetDateTime sourceStartedAt = OffsetDateTime.now();
            SourceSyncResultDto sourceResult = syncSourceInternal(sourceConfig, requestSource);
            OffsetDateTime sourceEndedAt = OffsetDateTime.now();
            sourceDurations.put(sourceConfig.getSourceType(), Duration.between(sourceStartedAt, sourceEndedAt));
            results.add(sourceResult);
        }

        SyncRunResponseDto summary = buildSummary(startedAt, results);
        discordNotifierService.notifySyncFinished(requestSource, null, summary, sourceDurations);
        return summary;
    }

    public SyncRunResponseDto syncSingle(PublicDataSourceType sourceType, SyncRequestSource requestSource) {
        BridgeWorkSyncProperties.SourceConfig sourceConfig = findSourceConfig(sourceType);
        if (!sourceConfig.isEnabled()) {
            throw new SyncSourceDisabledException(sourceType);
        }

        OffsetDateTime startedAt = OffsetDateTime.now();
        discordNotifierService.notifySyncStarted(requestSource, sourceType, startedAt);
        OffsetDateTime sourceStartedAt = OffsetDateTime.now();
        SourceSyncResultDto sourceResult = syncSourceInternal(sourceConfig, requestSource);
        OffsetDateTime sourceEndedAt = OffsetDateTime.now();
        List<SourceSyncResultDto> results = List.of(sourceResult);
        Map<PublicDataSourceType, Duration> sourceDurations = Map.of(
                sourceType,
                Duration.between(sourceStartedAt, sourceEndedAt)
        );
        SyncRunResponseDto summary = buildSummary(startedAt, results);
        discordNotifierService.notifySyncFinished(requestSource, sourceType, summary, sourceDurations);
        return summary;
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
                        resolveDisplayStatus(logItem),
                        logItem.getProcessedCount(),
                        logItem.getNewCount(),
                        logItem.getUpdatedCount(),
                        logItem.getFailedCount(),
                        logItem.getErrorMessage(),
                        toSeoulOffset(logItem.getStartedAt()),
                        toSeoulOffset(logItem.getEndedAt())
                ))
                .toList();
    }

    private SyncStatus resolveDisplayStatus(PublicDataSyncLog logItem) {
        if (logItem.getEndedAt() == null) {
            return SyncStatus.IN_PROGRESS;
        }
        return logItem.getStatus();
    }

    private OffsetDateTime toSeoulOffset(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        // DB에는 절대시각을 저장하고, API 응답 직전에 KST로 표준화한다.
        return value.atZoneSameInstant(SEOUL_ZONE_ID).toOffsetDateTime();
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

    public NormalizedCountSummaryResponseDto getNormalizedCounts() {
        List<NormalizedSourceCountResponseDto> sourceCounts = new ArrayList<>();
        long totalCount = 0L;

        for (PublicDataSourceType sourceType : PublicDataSourceType.values()) {
            String tableName = publicDataNormalizedStoreService.resolveTableName(sourceType);
            long rowCount = publicDataNormalizedStoreService.countBySource(sourceType);
            sourceCounts.add(new NormalizedSourceCountResponseDto(sourceType, tableName, rowCount));
            totalCount += rowCount;
        }

        return new NormalizedCountSummaryResponseDto(totalCount, sourceCounts);
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
        int closedCount = 0;
        SyncStatus syncStatus = SyncStatus.SUCCESS;
        String message = "동기화 완료";
        Set<String> fetchedExternalIds = new HashSet<>();
        Set<String> seenExternalIds = new HashSet<>();
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

            Map<String, ExistingRecordState> existingRecords = indexExistingRecords(sourceType);

            for (int pageNo = 1; pageNo <= sourceConfig.getMaxPages(); pageNo++) {
                PublicDataApiPageResponseDto pageResponse = publicDataApiClient.fetchPage(sourceConfig, pageNo);

                if (pageResponse.items().isEmpty()) {
                    break;
                }

                for (PublicDataApiItemDto item : pageResponse.items()) {
                    // 동일 호출 내 중복 응답은 마지막 상태와 무관하게 1건으로 처리한다.
                    if (!seenExternalIds.add(item.externalId())) {
                        continue;
                    }
                    if (isExpiredItem(sourceType, item)) {
                        continue;
                    }

                    fetchedExternalIds.add(item.externalId());
                    processedCount++;
                    try {
                        OffsetDateTime fetchedAt = OffsetDateTime.now();
                        UpsertResult upsertResult = upsertRecord(sourceType, item, existingRecords);
                        // 변경 또는 재활성화 건만 정규화 테이블에 반영한다.
                        if (upsertResult != UpsertResult.UNCHANGED) {
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
                if (shouldCloseMissingRecords(sourceType)) {
                    OffsetDateTime statusChangedAt = OffsetDateTime.now();
                    int rawClosedCount = closeMissingRecords(sourceType, fetchedExternalIds, statusChangedAt);
                    int normalizedClosedCount = sourceType == PublicDataSourceType.KEPAD_RECRUITMENT
                            ? publicDataNormalizedStoreService.closeMissingRecruitments(fetchedExternalIds, statusChangedAt)
                            : publicDataNormalizedStoreService.deleteMissing(sourceType, fetchedExternalIds);
                    closedCount = Math.max(rawClosedCount, normalizedClosedCount);
                    if (closedCount > 0) {
                        message = "동기화 완료 (만료/마감 전환 " + closedCount + "건)";
                    }
                } else {
                    deletedCount = removeDeletedRecords(sourceType, fetchedExternalIds);
                    publicDataNormalizedStoreService.deleteMissing(sourceType, fetchedExternalIds);
                    if (deletedCount > 0) {
                        message = "동기화 완료 (삭제 " + deletedCount + "건)";
                    }
                }
            }

            if (failedCount > 0) {
                syncStatus = SyncStatus.PARTIAL_SUCCESS;
                message = buildFailureSummaryMessage("일부 데이터 저장 실패", failureReasonCounts);
            }

            if (failedCount > 0 && deletedCount == 0 && closedCount == 0) {
                log.warn("부분 실패로 상태 동기화를 건너뜀 source={} failedCount={}", sourceType, failedCount);
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

        return reasonText.replace('\n', ' ').replace('\r', ' ').trim();
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

    private Map<String, ExistingRecordState> indexExistingRecords(PublicDataSourceType sourceType) {
        List<PublicDataRecordRepository.RecordStateView> states =
                publicDataRecordRepository.findRecordStateBySourceType(sourceType);
        Map<String, ExistingRecordState> indexedStates = new HashMap<>(Math.max(16, states.size() * 2));
        for (PublicDataRecordRepository.RecordStateView state : states) {
            indexedStates.put(
                    state.getExternalId(),
                    new ExistingRecordState(state.getPayloadHash(), state.getSyncStatus())
            );
        }
        return indexedStates;
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

    private int closeMissingRecords(PublicDataSourceType sourceType,
                                    Set<String> fetchedExternalIds,
                                    OffsetDateTime statusChangedAt) {
        if (fetchedExternalIds == null || fetchedExternalIds.isEmpty()) {
            return publicDataRecordRepository.markAllAsStatusBySourceType(
                    sourceType,
                    RecordSyncStatus.CLOSED,
                    statusChangedAt
            );
        }

        List<PublicDataRecordRepository.RecordIdentityView> existingRecords =
                publicDataRecordRepository.findRecordIdentityBySourceType(sourceType);
        List<Long> idsToClose = new ArrayList<>();

        for (PublicDataRecordRepository.RecordIdentityView existingRecord : existingRecords) {
            if (!fetchedExternalIds.contains(existingRecord.getExternalId())) {
                idsToClose.add(existingRecord.getId());
            }
        }

        if (idsToClose.isEmpty()) {
            return 0;
        }

        int closedCount = 0;
        for (int start = 0; start < idsToClose.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, idsToClose.size());
            List<Long> chunk = idsToClose.subList(start, end);
            closedCount += publicDataRecordRepository.markAllByIdInAsStatusNative(
                    chunk,
                    RecordSyncStatus.CLOSED.name(),
                    statusChangedAt
            );
        }
        return closedCount;
    }

    private UpsertResult upsertRecord(PublicDataSourceType sourceType,
                                      PublicDataApiItemDto item,
                                      Map<String, ExistingRecordState> existingRecords) {
        ExistingRecordState existingState = existingRecords.get(item.externalId());
        if (existingState != null
                && existingState.syncStatus() == RecordSyncStatus.ACTIVE
                && Objects.equals(existingState.payloadHash(), item.payloadHash())) {
            return UpsertResult.UNCHANGED;
        }

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
            record.setSyncStatus(RecordSyncStatus.ACTIVE);
            record.setClosedAt(null);
            record.setStatusUpdatedAt(now);
            PublicDataRecord savedRecord = publicDataRecordRepository.save(record);
            publicDataRecordFieldService.replaceFields(savedRecord);
            existingRecords.put(item.externalId(), new ExistingRecordState(item.payloadHash(), RecordSyncStatus.ACTIVE));
            return UpsertResult.INSERTED;
        }

        PublicDataRecord record = existingRecord.get();
        record.setRawFetchedAt(now);
        boolean wasClosed = record.getSyncStatus() != RecordSyncStatus.ACTIVE;
        record.setSyncStatus(RecordSyncStatus.ACTIVE);
        record.setClosedAt(null);
        if (wasClosed) {
            record.setStatusUpdatedAt(now);
        }

        // 동일 키 데이터는 해시를 비교해 변경 건만 업데이트한다.
        if (!Objects.equals(record.getPayloadHash(), item.payloadHash())) {
            record.setPayloadJson(item.payloadJson());
            record.setPayloadHash(item.payloadHash());
            PublicDataRecord savedRecord = publicDataRecordRepository.save(record);
            publicDataRecordFieldService.replaceFields(savedRecord);
            existingRecords.put(item.externalId(), new ExistingRecordState(item.payloadHash(), RecordSyncStatus.ACTIVE));
            return UpsertResult.UPDATED;
        }

        publicDataRecordRepository.save(record);
        existingRecords.put(item.externalId(), new ExistingRecordState(item.payloadHash(), RecordSyncStatus.ACTIVE));
        return wasClosed ? UpsertResult.UPDATED : UpsertResult.UNCHANGED;
    }

    private boolean shouldCloseMissingRecords(PublicDataSourceType sourceType) {
        return sourceType == PublicDataSourceType.KEPAD_RECRUITMENT
                || sourceType == PublicDataSourceType.VOCATIONAL_TRAINING
                || sourceType == PublicDataSourceType.JOBSEEKER_COMPETENCY_PROGRAM;
    }

    private boolean isExpiredItem(PublicDataSourceType sourceType, PublicDataApiItemDto item) {
        return resolveItemEndDate(sourceType, item)
                .map(endDate -> endDate.isBefore(LocalDate.now(SEOUL_ZONE_ID)))
                .orElse(false);
    }

    private Optional<LocalDate> resolveItemEndDate(PublicDataSourceType sourceType, PublicDataApiItemDto item) {
        String endDateField = switch (sourceType) {
            case KEPAD_RECRUITMENT -> "termDate";
            case VOCATIONAL_TRAINING -> "traEndDate";
            case JOBSEEKER_COMPETENCY_PROGRAM -> "pgmEndt";
            default -> null;
        };
        if (endDateField == null) {
            return Optional.empty();
        }

        try {
            JsonNode payloadNode = objectMapper.readTree(item.payloadJson());
            String rawDateText = payloadNode.path(endDateField).asText("");
            return extractLastDate(rawDateText);
        } catch (Exception exception) {
            log.warn("만료일 해석 실패 source={} externalId={} reason={}",
                    sourceType,
                    item.externalId(),
                    exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<LocalDate> extractLastDate(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }

        List<DateCandidate> dates = new ArrayList<>();
        collectDateMatches(rawText, COMPACT_DATE_PATTERN, dates);
        collectDateMatches(rawText, SEPARATED_DATE_PATTERN, dates);
        if (dates.isEmpty()) {
            return Optional.empty();
        }
        dates.sort((left, right) -> Integer.compare(left.startIndex(), right.startIndex()));
        return Optional.of(dates.get(dates.size() - 1).date());
    }

    private void collectDateMatches(String rawText, Pattern pattern, List<DateCandidate> dates) {
        Matcher matcher = pattern.matcher(rawText);
        while (matcher.find()) {
            try {
                if (pattern == COMPACT_DATE_PATTERN) {
                    dates.add(new DateCandidate(
                            matcher.start(),
                            LocalDate.parse(matcher.group(1) + matcher.group(2) + matcher.group(3), YYYYMMDD_FORMATTER)
                    ));
                } else {
                    dates.add(new DateCandidate(
                            matcher.start(),
                            LocalDate.of(
                                    Integer.parseInt(matcher.group(1)),
                                    Integer.parseInt(matcher.group(2)),
                                    Integer.parseInt(matcher.group(3))
                            )
                    ));
                }
            } catch (RuntimeException exception) {
                // 잘못된 날짜 토큰은 무시한다.
            }
        }
    }

    private PublicDataSyncLog createSyncLog(PublicDataSourceType sourceType, SyncRequestSource requestSource) {
        PublicDataSyncLog syncLog = new PublicDataSyncLog();
        syncLog.setSourceType(sourceType);
        syncLog.setRequestSource(requestSource);
        syncLog.setStatus(SyncStatus.IN_PROGRESS);
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
                : sanitizeMessage(message));
        syncLog.setEndedAt(OffsetDateTime.now());
        publicDataSyncLogRepository.save(syncLog);
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "오류 메시지가 비어 있습니다.";
        }
        return message;
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

    private record ExistingRecordState(String payloadHash, RecordSyncStatus syncStatus) {
    }

    private record DateCandidate(int startIndex, LocalDate date) {
    }
}
