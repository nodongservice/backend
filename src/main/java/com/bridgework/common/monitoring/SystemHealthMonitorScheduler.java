package com.bridgework.common.monitoring;

import com.bridgework.common.config.BridgeWorkHealthMonitorProperties;
import com.bridgework.common.notification.DiscordNotifierService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class SystemHealthMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthMonitorScheduler.class);

    private static final String CHECK_SPRING_DB = "SPRING_DB";
    private static final String CHECK_FASTAPI_HEALTH = "FASTAPI_HEALTH";

    private final JdbcTemplate jdbcTemplate;
    private final WebClient webClient;
    private final BridgeWorkHealthMonitorProperties healthMonitorProperties;
    private final DiscordNotifierService discordNotifierService;
    private final Map<String, HealthFailureState> failureStates = new ConcurrentHashMap<>();

    public SystemHealthMonitorScheduler(JdbcTemplate jdbcTemplate,
                                        WebClient.Builder webClientBuilder,
                                        BridgeWorkHealthMonitorProperties healthMonitorProperties,
                                        DiscordNotifierService discordNotifierService) {
        this.jdbcTemplate = jdbcTemplate;
        this.webClient = webClientBuilder.build();
        this.healthMonitorProperties = healthMonitorProperties;
        this.discordNotifierService = discordNotifierService;
    }

    @Scheduled(
            fixedDelayString = "${bridgework.health-monitor.interval:PT60S}",
            initialDelayString = "${bridgework.health-monitor.interval:PT60S}"
    )
    @SchedulerLock(name = "systemHealthMonitorScheduler", lockAtLeastFor = "PT5S", lockAtMostFor = "PT2M")
    public void monitorSystemHealth() {
        if (!healthMonitorProperties.isEnabled()) {
            return;
        }

        evaluate(CHECK_SPRING_DB, this::checkSpringDbHealth);
        evaluate(CHECK_FASTAPI_HEALTH, this::checkFastApiHealth);
    }

    private void evaluate(String checkName, HealthCheckExecutor executor) {
        OffsetDateTime now = OffsetDateTime.now();
        HealthCheckResult result = executor.execute();
        HealthFailureState previous = failureStates.get(checkName);

        if (result.up()) {
            if (previous != null) {
                failureStates.remove(checkName);
                log.info("헬스체크 복구: {}", checkName);
                discordNotifierService.notifyHealthCheckRecovered(
                        checkName,
                        now,
                        "장애 지속 시간: " + formatDuration(Duration.between(previous.firstDetectedAt(), now))
                );
            }
            return;
        }

        if (previous == null) {
            failureStates.put(checkName, new HealthFailureState(now, now));
            log.warn("헬스체크 실패 감지: {} - {}", checkName, result.reason());
            discordNotifierService.notifyHealthCheckIssue(checkName, result.reason(), now);
            return;
        }

        Duration reminderInterval = healthMonitorProperties.getAlertReminderInterval();
        if (Duration.between(previous.lastNotifiedAt(), now).compareTo(reminderInterval) < 0) {
            return;
        }

        failureStates.put(checkName, new HealthFailureState(previous.firstDetectedAt(), now));
        log.warn("헬스체크 장애 지속: {} - {}", checkName, result.reason());
        discordNotifierService.notifyHealthCheckIssue(
                checkName,
                result.reason() + " (지속 시간: " + formatDuration(Duration.between(previous.firstDetectedAt(), now)) + ")",
                now
        );
    }

    private HealthCheckResult checkSpringDbHealth() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return HealthCheckResult.createUp();
        } catch (Exception exception) {
            return HealthCheckResult.createDown("Spring DB 연결 확인 실패: " + summarize(exception));
        }
    }

    private HealthCheckResult checkFastApiHealth() {
        List<String> urls = healthMonitorProperties.getFastapiHealthUrls();
        if (urls.isEmpty()) {
            return HealthCheckResult.createDown("FastAPI health URL이 비어 있습니다.");
        }

        StringBuilder reasons = new StringBuilder();
        for (String url : urls) {
            HealthCheckResult result = checkHttpHealth(url);
            if (result.up()) {
                return result;
            }
            if (!reasons.isEmpty()) {
                reasons.append(" | ");
            }
            reasons.append(url).append(" -> ").append(result.reason());
        }

        return HealthCheckResult.createDown("모든 FastAPI health URL 실패: " + reasons);
    }

    private HealthCheckResult checkHttpHealth(String url) {
        try {
            String body = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(healthMonitorProperties.getRequestTimeout())
                    .block();

            if (body == null || body.isBlank()) {
                return HealthCheckResult.createDown("응답 본문이 비어 있습니다. url=" + url);
            }

            if (body.contains("\"status\":\"ok\"") || body.contains("\"success\":true") || body.contains("\"code\":\"SUCCESS\"")) {
                return HealthCheckResult.createUp();
            }

            return HealthCheckResult.createDown("비정상 응답 본문: " + compact(body));
        } catch (Exception exception) {
            return HealthCheckResult.createDown("요청 실패(url=" + url + "): " + summarize(exception));
        }
    }

    private String summarize(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }

        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }

        return root.getClass().getSimpleName() + ": " + compact(message);
    }

    private String compact(String message) {
        if (message == null) {
            return "";
        }

        String compact = message.replaceAll("\\s+", " ").trim();
        int maxLength = 300;
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "계산 불가";
        }

        Duration safe = duration.isNegative() ? duration.abs() : duration;
        long seconds = safe.toSeconds();
        long minutes = seconds / 60;
        long remainSeconds = seconds % 60;
        return minutes + "분 " + remainSeconds + "초";
    }

    @FunctionalInterface
    private interface HealthCheckExecutor {

        HealthCheckResult execute();
    }

    private record HealthCheckResult(boolean up, String reason) {

        static HealthCheckResult createUp() {
            return new HealthCheckResult(true, "");
        }

        static HealthCheckResult createDown(String reason) {
            return new HealthCheckResult(false, reason == null ? "unknown" : reason);
        }
    }

    private record HealthFailureState(OffsetDateTime firstDetectedAt, OffsetDateTime lastNotifiedAt) {
    }
}
