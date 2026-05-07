package com.bridgework.sync.service;

import com.bridgework.sync.entity.SyncRequestSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PublicDataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublicDataSyncScheduler.class);

    private final PublicDataSyncService publicDataSyncService;
    private final PublicDataSyncExecutionLockService publicDataSyncExecutionLockService;

    public PublicDataSyncScheduler(PublicDataSyncService publicDataSyncService,
                                   PublicDataSyncExecutionLockService publicDataSyncExecutionLockService) {
        this.publicDataSyncService = publicDataSyncService;
        this.publicDataSyncExecutionLockService = publicDataSyncExecutionLockService;
    }

    @Scheduled(cron = "${bridgework.sync.cron}", zone = "${bridgework.sync.cron-zone:Asia/Seoul}")
    public void syncPublicData() {
        // 수동 실행과 스케줄러 실행이 겹치지 않도록 동일 전역 락을 사용한다.
        boolean executed = publicDataSyncExecutionLockService.runSchedulerIfAvailable(
                () -> publicDataSyncService.syncAll(SyncRequestSource.SCHEDULER)
        );
        if (executed) {
            log.info("정기 동기화 작업 완료");
        } else {
            log.info("정기 동기화 건너뜀: 이미 다른 동기화 작업이 진행 중");
        }
    }
}
