package com.bridgework;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.common.config.BridgeWorkDiscordProperties;
import com.bridgework.common.config.BridgeWorkHealthMonitorProperties;
import com.bridgework.recommend.config.BridgeWorkRecommendProperties;
import com.bridgework.sync.config.BridgeWorkSyncProperties;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
@SpringBootApplication
@EnableConfigurationProperties({
        BridgeWorkDiscordProperties.class,
        BridgeWorkHealthMonitorProperties.class,
        BridgeWorkSyncProperties.class,
        BridgeWorkAuthProperties.class,
        BridgeWorkRecommendProperties.class
})
public class BridgeWorkApplication {

    public static void main(String[] args) {
        SpringApplication.run(BridgeWorkApplication.class, args);
    }
}
