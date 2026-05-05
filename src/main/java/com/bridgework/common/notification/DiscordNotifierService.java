package com.bridgework.common.notification;

import com.bridgework.common.config.BridgeWorkDiscordProperties;
import com.bridgework.sync.dto.SourceSyncResultDto;
import com.bridgework.sync.dto.SyncRunResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.SyncRequestSource;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

@Service
public class DiscordNotifierService {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifierService.class);
    private static final int DISCORD_MAX_MESSAGE_LENGTH = 1900;
    private static final int DISCORD_RETRY_COUNT = 2;
    private static final Duration DISCORD_RETRY_BACKOFF = Duration.ofMillis(700);
    private static final Duration DISCORD_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final BridgeWorkDiscordProperties discordProperties;

    public DiscordNotifierService(WebClient.Builder webClientBuilder,
                                  BridgeWorkDiscordProperties discordProperties) {
        this.webClient = webClientBuilder.build();
        this.discordProperties = discordProperties;
    }

    public void notifySyncFinished(SyncRequestSource requestSource,
                                   PublicDataSourceType requestedSourceType,
                                   SyncRunResponseDto result,
                                   Map<PublicDataSourceType, Duration> sourceDurations) {
        if (result == null) {
            return;
        }

        String requestSourceText = requestSource == SyncRequestSource.MANUAL ? "수동" : "스케줄러";
        String targetText = requestedSourceType == null ? "전체 소스" : requestedSourceType.name();
        String failedSourceSummary = result.results().stream()
                .filter(sourceResult -> sourceResult.failedCount() > 0)
                .map(sourceResult -> sourceResult.sourceType().name() + "(" + sourceResult.failedCount() + "건)")
                .collect(Collectors.joining(", "));

        StringBuilder builder = new StringBuilder();
        builder.append("안녕하세요! 브릿지워크 동기화 알림 봇입니다.\n");
        builder.append("요청 구분: ").append(requestSourceText).append('\n');
        builder.append("대상: ").append(targetText).append('\n');
        builder.append("시작: ").append(result.startedAt()).append('\n');
        builder.append("종료: ").append(result.endedAt()).append('\n');
        builder.append("소요 시간: ").append(formatElapsed(result.startedAt(), result.endedAt())).append('\n');
        builder.append("처리: ").append(result.processedCount())
                .append("건 / 신규: ").append(result.newCount())
                .append("건 / 수정: ").append(result.updatedCount())
                .append("건 / 실패: ").append(result.failedCount()).append("건");
        if (!failedSourceSummary.isBlank()) {
            builder.append('\n').append("실패 소스: ").append(failedSourceSummary);
        }
        appendFailureDetails(builder, result);
        appendPerSourceElapsed(builder, result, sourceDurations);

        send(builder.toString());
    }

    public void notifySyncStarted(SyncRequestSource requestSource,
                                  PublicDataSourceType requestedSourceType,
                                  java.time.OffsetDateTime startedAt) {
        String requestSourceText = requestSource == SyncRequestSource.MANUAL ? "수동" : "스케줄러";
        String targetText = requestedSourceType == null ? "전체 소스" : requestedSourceType.name();

        String message = "안녕하세요! 브릿지워크 동기화 알림 봇입니다.\n"
                + "동기화 작업을 시작했어요.\n"
                + "요청 구분: " + requestSourceText + '\n'
                + "대상: " + targetText + '\n'
                + "시작 시각: " + startedAt;
        send(message);
    }

    public void notifySignupCompleted(String email, long totalUserCount) {
        String safeEmail = (email == null || email.isBlank()) ? "(이메일 없음)" : email;
        String message = "안녕하세요! 브릿지워크 가입 알림 봇입니다.\n"
                + "신규 회원이 가입을 완료했어요.\n"
                + "이메일: " + safeEmail + '\n'
                + "현재 회원 수: " + totalUserCount + "명";
        send(message);
    }

    private void send(String content) {
        String webhookUrl = discordProperties.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        String normalizedContent = truncate(content);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", normalizedContent);
        body.put("allowed_mentions", Map.of("parse", new String[0]));

        try {
            webClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(DISCORD_REQUEST_TIMEOUT)
                    .retryWhen(
                            Retry.backoff(DISCORD_RETRY_COUNT, DISCORD_RETRY_BACKOFF)
                                    .filter(this::isRetryableDiscordException)
                    )
                    .block();
        } catch (Exception exception) {
            // 알림 실패가 본 업무 플로우를 막지 않도록 예외를 삼킨다.
            log.warn("Discord 알림 전송 실패: {}", summarizeFailureReason(exception));
        }
    }

    private String truncate(String content) {
        if (content == null || content.isBlank()) {
            return "안녕하세요! 브릿지워크 알림 봇입니다. 비어 있는 메시지를 감지했습니다.";
        }
        if (content.length() <= DISCORD_MAX_MESSAGE_LENGTH) {
            return content;
        }
        return content.substring(0, DISCORD_MAX_MESSAGE_LENGTH - 3) + "...";
    }

    private String formatElapsed(OffsetDateTime startedAt, OffsetDateTime endedAt) {
        if (startedAt == null || endedAt == null) {
            return "계산 불가";
        }

        Duration elapsed = Duration.between(startedAt, endedAt);
        return formatElapsed(elapsed);
    }

    private String formatElapsed(Duration elapsed) {
        if (elapsed == null) {
            return "계산 불가";
        }
        if (elapsed.isNegative()) {
            elapsed = elapsed.abs();
        }

        long totalSeconds = elapsed.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "분 " + seconds + "초";
    }

    private void appendPerSourceElapsed(StringBuilder builder,
                                        SyncRunResponseDto result,
                                        Map<PublicDataSourceType, Duration> sourceDurations) {
        if (sourceDurations == null || sourceDurations.isEmpty()) {
            return;
        }
        List<String> lines = result.results().stream()
                .map(sourceResult -> {
                    Duration duration = sourceDurations.get(sourceResult.sourceType());
                    if (duration == null) {
                        return null;
                    }
                    return sourceResult.sourceType().name() + ": " + formatElapsed(duration);
                })
                .filter(line -> line != null && !line.isBlank())
                .toList();
        if (lines.isEmpty()) {
            return;
        }

        builder.append('\n').append("소스별 소요 시간:");
        for (String line : lines) {
            builder.append('\n').append("- ").append(line);
        }
    }

    private void appendFailureDetails(StringBuilder builder, SyncRunResponseDto result) {
        List<String> failureLines = result.results().stream()
                .filter(sourceResult -> sourceResult.failedCount() > 0)
                .map(sourceResult -> {
                    String message = sourceResult.message();
                    if (message == null || message.isBlank()) {
                        message = "오류 메시지 없음";
                    }
                    return sourceResult.sourceType().name() + ": " + truncateFailureMessage(message);
                })
                .toList();
        if (failureLines.isEmpty()) {
            return;
        }

        builder.append('\n').append("실패 상세:");
        int maxLines = Math.min(5, failureLines.size());
        for (int index = 0; index < maxLines; index++) {
            builder.append('\n').append("- ").append(failureLines.get(index));
        }
        if (failureLines.size() > maxLines) {
            builder.append('\n').append("- ... 외 ")
                    .append(failureLines.size() - maxLines)
                    .append("개 소스");
        }
    }

    private String truncateFailureMessage(String message) {
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 177) + "...";
    }

    private boolean isRetryableDiscordException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != current) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                if (message.contains("Did not observe any item or terminal signal")
                        || message.contains("Read timed out")
                        || message.contains("Connection reset")
                        || message.contains("Failed to resolve")
                        || message.contains("UnknownHostException")
                        || message.contains("connection prematurely closed")
                        || message.contains("TLS")
                        || message.contains("SSL")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String summarizeFailureReason(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable == null ? null : throwable.getMessage();
        }
        if (message == null || message.isBlank()) {
            message = throwable == null ? "unknown" : throwable.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
