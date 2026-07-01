package com.bridgework.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.common.notification.DiscordNotifierService;
import com.bridgework.sync.config.BridgeWorkSyncProperties;
import com.bridgework.sync.dto.PublicDataApiItemDto;
import com.bridgework.sync.dto.PublicDataApiPageResponseDto;
import com.bridgework.sync.dto.SyncRunResponseDto;
import com.bridgework.sync.entity.PublicDataRecord;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.PublicDataSyncLog;
import com.bridgework.sync.entity.RecordSyncStatus;
import com.bridgework.sync.entity.SyncRequestSource;
import com.bridgework.sync.normalized.PublicDataNormalizedStoreService;
import com.bridgework.sync.repository.PublicDataRecordRepository;
import com.bridgework.sync.repository.PublicDataSourceSnapshotRepository;
import com.bridgework.sync.repository.PublicDataSyncLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicDataSyncServiceTest {

    @Mock
    private PublicDataApiClient publicDataApiClient;
    @Mock
    private PublicDataRecordRepository publicDataRecordRepository;
    @Mock
    private PublicDataRecordFieldService publicDataRecordFieldService;
    @Mock
    private PublicDataSyncLogRepository publicDataSyncLogRepository;
    @Mock
    private PublicDataSourceSnapshotRepository publicDataSourceSnapshotRepository;
    @Mock
    private PublicDataNormalizedStoreService publicDataNormalizedStoreService;
    @Mock
    private DiscordNotifierService discordNotifierService;

    private PublicDataSyncService publicDataSyncService;

    @BeforeEach
    void setUp() {
        when(publicDataSyncLogRepository.save(any(PublicDataSyncLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        publicDataSyncService = new PublicDataSyncService(
                syncProperties(PublicDataSourceType.KEPAD_RECRUITMENT),
                publicDataApiClient,
                publicDataRecordRepository,
                publicDataRecordFieldService,
                publicDataSyncLogRepository,
                publicDataSourceSnapshotRepository,
                publicDataNormalizedStoreService,
                discordNotifierService,
                new ObjectMapper()
        );
    }

    @Test
    void syncSingle_whenExistingActiveRecordUnchanged_thenDoesNotWriteRawOrNormalizedTables() {
        PublicDataApiItemDto item = new PublicDataApiItemDto(
                "J-1",
                "{\"joReqstNo\":\"J-1\",\"termDate\":\"29991231\"}",
                "hash-1"
        );
        when(publicDataApiClient.fetchLatestRevision(any())).thenReturn(Optional.empty());
        when(publicDataRecordRepository.findRecordStateBySourceType(PublicDataSourceType.KEPAD_RECRUITMENT))
                .thenReturn(List.of(recordState("J-1", "hash-1", RecordSyncStatus.ACTIVE)));
        when(publicDataRecordRepository.findRecordIdentityBySourceType(PublicDataSourceType.KEPAD_RECRUITMENT))
                .thenReturn(List.of(recordIdentity(1L, "J-1")));
        when(publicDataApiClient.fetchPage(any(), eq(1)))
                .thenReturn(new PublicDataApiPageResponseDto(List.of(item), false));
        when(publicDataNormalizedStoreService.closeMissingRecruitments(anySet(), any(OffsetDateTime.class)))
                .thenReturn(0);

        SyncRunResponseDto response = publicDataSyncService.syncSingle(
                PublicDataSourceType.KEPAD_RECRUITMENT,
                SyncRequestSource.MANUAL
        );

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(response.newCount()).isZero();
        assertThat(response.updatedCount()).isZero();
        verify(publicDataRecordRepository, never()).findBySourceTypeAndExternalId(any(), any());
        verify(publicDataRecordRepository, never()).save(any(PublicDataRecord.class));
        verify(publicDataRecordRepository, never()).markMissingAsStatusBySourceType(any(), anySet(), any(), any());
        verify(publicDataRecordRepository, never()).markAllByIdInAsStatusNative(any(), any(), any());
        verify(publicDataRecordFieldService, never()).replaceFields(any());
        verify(publicDataNormalizedStoreService, never()).upsert(any(), any(), any());
        verify(publicDataNormalizedStoreService, never()).touch(any(), any(), any());
    }

    @Test
    void syncSingle_whenKepadRecruitmentExpiredInApiResponse_thenSkipsSaveAndClosesExistingRecords() {
        PublicDataApiItemDto expiredItem = new PublicDataApiItemDto(
                "J-OLD",
                "{\"joReqstNo\":\"J-OLD\",\"termDate\":\"20000101\"}",
                "hash-old"
        );
        when(publicDataApiClient.fetchLatestRevision(any())).thenReturn(Optional.empty());
        when(publicDataRecordRepository.findRecordStateBySourceType(PublicDataSourceType.KEPAD_RECRUITMENT))
                .thenReturn(List.of(recordState("J-OLD", "hash-old", RecordSyncStatus.ACTIVE)));
        when(publicDataApiClient.fetchPage(any(), eq(1)))
                .thenReturn(new PublicDataApiPageResponseDto(List.of(expiredItem), false));
        when(publicDataRecordRepository.markAllAsStatusBySourceType(
                eq(PublicDataSourceType.KEPAD_RECRUITMENT),
                eq(RecordSyncStatus.CLOSED),
                any(OffsetDateTime.class)
        )).thenReturn(1);
        when(publicDataNormalizedStoreService.closeMissingRecruitments(anySet(), any(OffsetDateTime.class)))
                .thenReturn(1);

        SyncRunResponseDto response = publicDataSyncService.syncSingle(
                PublicDataSourceType.KEPAD_RECRUITMENT,
                SyncRequestSource.MANUAL
        );

        assertThat(response.processedCount()).isZero();
        assertThat(response.newCount()).isZero();
        assertThat(response.updatedCount()).isZero();
        verify(publicDataRecordRepository, never()).findBySourceTypeAndExternalId(any(), any());
        verify(publicDataRecordRepository, never()).save(any(PublicDataRecord.class));
        verify(publicDataNormalizedStoreService, never()).upsert(any(), any(), any());
        verify(publicDataRecordRepository).markAllAsStatusBySourceType(
                eq(PublicDataSourceType.KEPAD_RECRUITMENT),
                eq(RecordSyncStatus.CLOSED),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void syncSingle_whenExistingRecordMissingFromFetchedIds_thenClosesOnlyMissingRecordIdsInChunks() {
        PublicDataApiItemDto item = new PublicDataApiItemDto(
                "J-1",
                "{\"joReqstNo\":\"J-1\",\"termDate\":\"29991231\"}",
                "hash-1"
        );
        when(publicDataApiClient.fetchLatestRevision(any())).thenReturn(Optional.empty());
        when(publicDataRecordRepository.findRecordStateBySourceType(PublicDataSourceType.KEPAD_RECRUITMENT))
                .thenReturn(List.of(
                        recordState("J-1", "hash-1", RecordSyncStatus.ACTIVE),
                        recordState("J-MISSING", "hash-old", RecordSyncStatus.ACTIVE)
                ));
        when(publicDataRecordRepository.findRecordIdentityBySourceType(PublicDataSourceType.KEPAD_RECRUITMENT))
                .thenReturn(List.of(
                        recordIdentity(1L, "J-1"),
                        recordIdentity(2L, "J-MISSING")
                ));
        when(publicDataApiClient.fetchPage(any(), eq(1)))
                .thenReturn(new PublicDataApiPageResponseDto(List.of(item), false));
        when(publicDataRecordRepository.markAllByIdInAsStatusNative(
                eq(List.of(2L)),
                eq(RecordSyncStatus.CLOSED.name()),
                any(OffsetDateTime.class)
        )).thenReturn(1);
        when(publicDataNormalizedStoreService.closeMissingRecruitments(anySet(), any(OffsetDateTime.class)))
                .thenReturn(0);

        SyncRunResponseDto response = publicDataSyncService.syncSingle(
                PublicDataSourceType.KEPAD_RECRUITMENT,
                SyncRequestSource.MANUAL
        );

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(response.updatedCount()).isZero();
        verify(publicDataRecordRepository, never()).markMissingAsStatusBySourceType(any(), anySet(), any(), any());
        verify(publicDataRecordRepository).markAllByIdInAsStatusNative(
                eq(List.of(2L)),
                eq(RecordSyncStatus.CLOSED.name()),
                any(OffsetDateTime.class)
        );
    }

    private BridgeWorkSyncProperties syncProperties(PublicDataSourceType sourceType) {
        BridgeWorkSyncProperties.SourceConfig sourceConfig = new BridgeWorkSyncProperties.SourceConfig();
        sourceConfig.setEnabled(true);
        sourceConfig.setSourceType(sourceType);
        sourceConfig.setBaseUrl("https://example.com/api");
        sourceConfig.setServiceKey("test-key");
        sourceConfig.setPageSize(1000);
        sourceConfig.setMaxPages(1);
        sourceConfig.setItemIdField("joReqstNo");

        BridgeWorkSyncProperties properties = new BridgeWorkSyncProperties();
        properties.setSources(List.of(sourceConfig));
        return properties;
    }

    private PublicDataRecordRepository.RecordStateView recordState(
            String externalId,
            String payloadHash,
            RecordSyncStatus syncStatus
    ) {
        return new PublicDataRecordRepository.RecordStateView() {
            @Override
            public String getExternalId() {
                return externalId;
            }

            @Override
            public String getPayloadHash() {
                return payloadHash;
            }

            @Override
            public RecordSyncStatus getSyncStatus() {
                return syncStatus;
            }
        };
    }

    private PublicDataRecordRepository.RecordIdentityView recordIdentity(Long id, String externalId) {
        return new PublicDataRecordRepository.RecordIdentityView() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getExternalId() {
                return externalId;
            }
        };
    }
}
