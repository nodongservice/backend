package com.bridgework.sync.service;

import com.bridgework.sync.entity.SyncRequestSource;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PublicDataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublicDataSyncScheduler.class);

    private final PublicDataSyncService publicDataSyncService;

    public PublicDataSyncScheduler(PublicDataSyncService publicDataSyncService) {
        this.publicDataSyncService = publicDataSyncService;
    }

    @Scheduled(cron = "${bridgework.sync.cron}")
    @SchedulerLock(name = "publicDataSyncScheduler", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void syncPublicData() {
        // 멀티 인스턴스 환경에서 중복 실행을 막기 위해 ShedLock을 적용한다.
        publicDataSyncService.syncAll(SyncRequestSource.SCHEDULER);
        log.info("정기 동기화 작업 완료");
    }
}
