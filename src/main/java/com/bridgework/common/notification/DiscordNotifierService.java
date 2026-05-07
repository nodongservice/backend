package com.bridgework.common.notification;

import com.bridgework.common.config.BridgeWorkDiscordProperties;
import com.bridgework.sync.dto.SourceSyncResultDto;
import com.bridgework.sync.dto.SyncRunResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.SyncRequestSource;
import com.bridgework.sync.entity.SyncStatus;
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
    private static final String HEADER_SYNC_STARTED = "🚀 [공공데이터 동기화 시작 알림]";
    private static final String HEADER_SYNC_FINISHED_SUCCESS = "✅ [공공데이터 동기화 완료 알림]";
    private static final String HEADER_SYNC_FINISHED_FAILED = "❌ [공공데이터 동기화 완료 알림]";
    private static final String HEADER_SIGNUP_COMPLETED = "🎉 [회원가입 완료 알림]";
    private static final String HEADER_ADMIN_LOCKED = "🔒 [관리자 계정 잠금 알림]";
    private static final String HEADER_GENERIC = "ℹ️ [시스템 알림]";

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
        builder.append(resolveSyncFinishedHeader(result)).append('\n');
        builder.append("요청 구분: ").append(requestSourceText).append('\n');
        builder.append("대상: ").append(targetText).append('\n');
        builder.append("시작: ").append(result.startedAt()).append('\n');
        builder.append("종료: ").append(result.endedAt()).append('\n');
        builder.append("소요 시간: ").append(formatElapsed(result.startedAt(), result.endedAt())).append('\n');
        builder.append("결과: ").append(resolveSyncResultLabel(result)).append('\n');
        builder.append("처리: ").append(result.processedCount())
                .append("건 / 신규: ").append(result.newCount())
                .append("건 / 수정: ").append(result.updatedCount())
                .append("건 / 실패: ").append(result.failedCount()).append("건");
        if (!failedSourceSummary.isBlank()) {
            builder.append('\n').append("실패 소스: ").append(failedSourceSummary);
        }
        appendFailureDetails(builder, result);
        appendPerSourceDetails(builder, requestedSourceType, result, sourceDurations);
        builder.append('\n').append('\n');

        send(builder.toString());
    }

    public void notifySyncStarted(SyncRequestSource requestSource,
                                  PublicDataSourceType requestedSourceType,
                                  java.time.OffsetDateTime startedAt) {
        String requestSourceText = requestSource == SyncRequestSource.MANUAL ? "수동" : "스케줄러";
        String targetText = requestedSourceType == null ? "전체 소스" : requestedSourceType.name();

        String message = HEADER_SYNC_STARTED + '\n'
                + "요청 구분: " + requestSourceText + '\n'
                + "대상: " + targetText + '\n'
                + "시작 시각: " + startedAt + '\n'
                + '\n';
        send(message);
    }

    public void notifySignupCompleted(String email, long totalUserCount) {
        String safeEmail = (email == null || email.isBlank()) ? "(이메일 없음)" : email;
        String message = HEADER_SIGNUP_COMPLETED + '\n'
                + "이메일: " + safeEmail + '\n'
                + "현재 회원 수: " + totalUserCount + "명\n"
                + '\n';
        send(message);
    }

    public void notifyAdminAccountLocked(String loginId, OffsetDateTime lockedUntil, String reason) {
        String safeLoginId = (loginId == null || loginId.isBlank()) ? "(loginId 없음)" : loginId;
        String safeLockedUntil = lockedUntil == null ? "(미확인)" : lockedUntil.toString();
        String safeReason = (reason == null || reason.isBlank()) ? "사유 미기록" : reason;

        String message = HEADER_ADMIN_LOCKED + '\n'
                + "loginId: " + safeLoginId + '\n'
                + "잠금 해제 시각(UTC): " + safeLockedUntil + '\n'
                + "사유: " + safeReason + '\n'
                + '\n';
        send(message);
    }

    private void send(String content) {
        String webhookUrl = discordProperties.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        for (String chunk : splitContentPreservingAllText(content)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("content", chunk);
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
                return;
            }
        }
    }

    private List<String> splitContentPreservingAllText(String content) {
        if (content == null || content.isBlank()) {
            return List.of(HEADER_GENERIC + "\n비어 있는 메시지를 감지했습니다.\n\n");
        }

        List<String> chunks = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] lines = content.split("\n", -1);

        for (String line : lines) {
            String lineWithNewline = line + "\n";
            if (lineWithNewline.length() > DISCORD_MAX_MESSAGE_LENGTH) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                // 단일 라인이 제한보다 길면 순서 보존을 위해 고정 길이로 연속 분할한다.
                int start = 0;
                while (start < lineWithNewline.length()) {
                    int end = Math.min(start + DISCORD_MAX_MESSAGE_LENGTH, lineWithNewline.length());
                    chunks.add(lineWithNewline.substring(start, end));
                    start = end;
                }
                continue;
            }

            if (current.length() + lineWithNewline.length() > DISCORD_MAX_MESSAGE_LENGTH) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(lineWithNewline);
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
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

    private void appendPerSourceDetails(StringBuilder builder,
                                        PublicDataSourceType requestedSourceType,
                                        SyncRunResponseDto result,
                                        Map<PublicDataSourceType, Duration> sourceDurations) {
        if (requestedSourceType != null) {
            // 단일 소스 동기화는 상단 요약의 소요 시간으로 충분하므로 소스별 상세를 생략한다.
            return;
        }
        if (result.results() == null || result.results().isEmpty()) {
            return;
        }

        builder.append('\n').append("상세 내용:");
        for (SourceSyncResultDto sourceResult : result.results()) {
            Duration duration = sourceDurations == null
                    ? null
                    : sourceDurations.get(sourceResult.sourceType());
            String elapsedText = formatElapsed(duration);
            builder.append('\n')
                    .append("- ")
                    .append(sourceResult.sourceType().name())
                    .append(" | 상태: ").append(formatStatusWithEmoji(sourceResult.status()))
                    .append(" | 처리: ").append(sourceResult.processedCount()).append("건")
                    .append(" | 신규: ").append(sourceResult.newCount()).append("건")
                    .append(" | 수정: ").append(sourceResult.updatedCount()).append("건")
                    .append(" | 실패: ").append(sourceResult.failedCount()).append("건")
                    .append(" | 소요 시간: ").append(elapsedText);
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
                    return sourceResult.sourceType().name() + ": " + normalizeFailureMessage(message);
                })
                .toList();
        if (failureLines.isEmpty()) {
            return;
        }

        builder.append('\n').append("실패 상세:");
        for (String failureLine : failureLines) {
            builder.append('\n').append("- ").append(failureLine);
        }
    }

    private String normalizeFailureMessage(String message) {
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String resolveSyncFinishedHeader(SyncRunResponseDto result) {
        if (hasSyncFailure(result)) {
            return HEADER_SYNC_FINISHED_FAILED;
        }
        return HEADER_SYNC_FINISHED_SUCCESS;
    }

    private String resolveSyncResultLabel(SyncRunResponseDto result) {
        if (hasSyncFailure(result)) {
            return "❌ 실패";
        }
        return "✅ 성공";
    }

    private boolean hasSyncFailure(SyncRunResponseDto result) {
        if (result.failedCount() > 0) {
            return true;
        }
        if (result.results() == null || result.results().isEmpty()) {
            return false;
        }
        return result.results().stream().anyMatch(sourceResult -> sourceResult.status() != null
                && (sourceResult.status() == SyncStatus.FAILED
                || sourceResult.status() == SyncStatus.PARTIAL_SUCCESS));
    }

    private String formatStatusWithEmoji(SyncStatus status) {
        if (status == null) {
            return "⚪ UNKNOWN";
        }

        return switch (status) {
            case IN_PROGRESS -> "🔄 IN_PROGRESS";
            case SUCCESS -> "✅ SUCCESS";
            case FAILED -> "❌ FAILED";
            case PARTIAL_SUCCESS -> "⚠️ PARTIAL_SUCCESS";
            case SKIP -> "⏭️ SKIP";
        };
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
