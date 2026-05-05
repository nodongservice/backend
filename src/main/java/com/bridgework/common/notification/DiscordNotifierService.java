package com.bridgework.common.notification;

import com.bridgework.common.config.BridgeWorkDiscordProperties;
import com.bridgework.sync.dto.SourceSyncResultDto;
import com.bridgework.sync.dto.SyncRunResponseDto;
import com.bridgework.sync.entity.PublicDataSourceType;
import com.bridgework.sync.entity.SyncRequestSource;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class DiscordNotifierService {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifierService.class);
    private static final int DISCORD_MAX_MESSAGE_LENGTH = 1900;

    private final WebClient webClient;
    private final BridgeWorkDiscordProperties discordProperties;

    public DiscordNotifierService(WebClient.Builder webClientBuilder,
                                  BridgeWorkDiscordProperties discordProperties) {
        this.webClient = webClientBuilder.build();
        this.discordProperties = discordProperties;
    }

    public void notifySyncFinished(SyncRequestSource requestSource,
                                   PublicDataSourceType requestedSourceType,
                                   SyncRunResponseDto result) {
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
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception exception) {
            // 알림 실패가 본 업무 플로우를 막지 않도록 예외를 삼킨다.
            log.warn("Discord 알림 전송 실패: {}", exception.getMessage());
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
        if (elapsed.isNegative()) {
            elapsed = elapsed.abs();
        }

        long totalSeconds = elapsed.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "분 " + seconds + "초";
    }
}
