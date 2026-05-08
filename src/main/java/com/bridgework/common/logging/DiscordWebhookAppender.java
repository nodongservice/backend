package com.bridgework.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscordWebhookAppender extends AppenderBase<ILoggingEvent> {

    private static final int DISCORD_MAX_MESSAGE_LENGTH = 1900;
    private static final String HEADER_ERROR = "🚨 [Error 로그 발생]";

    private String webhookUrl;
    private String environment;
    private ExecutorService executorService;
    private HttpClient httpClient;

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    @Override
    public void start() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            addInfo("Discord webhook URL이 비어 있어 DiscordWebhookAppender를 비활성화한다.");
            return;
        }
        this.executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("discord-log-appender");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        super.start();
    }

    @Override
    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted()) {
            return;
        }
        if (!eventObject.getLevel().isGreaterOrEqual(Level.ERROR)) {
            return;
        }

        String payload = "{\"content\":\"" + escapeJson(buildMessage(eventObject)) + "\""
                + ",\"allowed_mentions\":{\"parse\":[]}}";

        executorService.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int statusCode = response.statusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    String responseBody = response.body();
                    if (responseBody == null || responseBody.isBlank()) {
                        responseBody = "(본문 없음)";
                    }
                    addError("Discord 로그 알림 전송 실패: status=" + statusCode + ", body=" + truncate(responseBody, 300));
                }
            } catch (Exception exception) {
                addError("Discord 로그 알림 전송 실패: " + exception.getMessage(), exception);
            }
        });
    }

    private String buildMessage(ILoggingEvent eventObject) {
        StringBuilder builder = new StringBuilder();
        builder.append(HEADER_ERROR).append('\n');
        builder.append("환경: ").append(safe(environment)).append('\n');
        builder.append("레벨: ").append(eventObject.getLevel()).append('\n');
        builder.append("로거: ").append(safe(eventObject.getLoggerName())).append('\n');
        builder.append("메시지: ").append(safe(eventObject.getFormattedMessage()));

        if (eventObject.getThrowableProxy() != null) {
            String stackTrace = ThrowableProxyUtil.asString(eventObject.getThrowableProxy());
            if (stackTrace != null && !stackTrace.isBlank()) {
                String compact = stackTrace.replace("\r", "");
                int lineBreakIndex = compact.indexOf('\n');
                String firstLine = lineBreakIndex < 0 ? compact : compact.substring(0, lineBreakIndex);
                builder.append('\n').append("예외: ").append(firstLine);
            }
        }
        builder.append('\n');

        String message = builder.toString();
        if (message.length() <= DISCORD_MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, DISCORD_MAX_MESSAGE_LENGTH - 3) + "...";
    }

    private String safe(String text) {
        if (text == null || text.isBlank()) {
            return "(없음)";
        }
        return text;
    }

    private String escapeJson(String content) {
        StringBuilder builder = new StringBuilder(content.length() + 32);
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            switch (current) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    builder.append(current);
            }
        }
        return builder.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String compact = text.replace("\r", " ").replace("\n", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }
}
