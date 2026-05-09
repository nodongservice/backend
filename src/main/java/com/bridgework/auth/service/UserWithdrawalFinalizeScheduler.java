package com.bridgework.auth.service;

import java.time.OffsetDateTime;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class UserWithdrawalFinalizeScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserWithdrawalFinalizeScheduler.class);

    private final AuthService authService;

    public UserWithdrawalFinalizeScheduler(AuthService authService) {
        this.authService = authService;
    }

    @Scheduled(
            fixedDelayString = "${bridgework.auth.withdrawal-finalize-interval:PT1H}",
            initialDelayString = "${bridgework.auth.withdrawal-finalize-interval:PT1H}"
    )
    @SchedulerLock(name = "userWithdrawalFinalizeScheduler", lockAtLeastFor = "PT5S", lockAtMostFor = "PT10M")
    public void finalizeDueWithdrawals() {
        int finalizedCount = authService.finalizeDueWithdrawals(OffsetDateTime.now());
        if (finalizedCount > 0) {
            log.info("탈퇴 유예기간 만료 계정 최종 처리 완료: {}건", finalizedCount);
        }
    }
}
