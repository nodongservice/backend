package com.bridgework.recommend.service;

import com.bridgework.profile.dto.UserProfileResponseDto;
import com.bridgework.profile.service.UserProfileService;
import com.bridgework.recommend.config.BridgeWorkRecommendProperties;
import com.bridgework.recommend.dto.RecommendAsyncResponseDto;
import com.bridgework.recommend.dto.RecommendRequestDto;
import com.bridgework.recommend.dto.RecommendTaskStatus;
import com.bridgework.recommend.exception.RecommendDomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RecommendAsyncTaskService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DAILY_CACHE_BOUNDARY_HOUR = 2;
    private static final String TASK_KEY_PREFIX = "recommend:task:";
    private static final String TASK_LOCK_KEY_PREFIX = "recommend:task:lock:";

    private final RecommendGatewayService recommendGatewayService;
    private final UserProfileService userProfileService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final BridgeWorkRecommendProperties recommendProperties;
    private final Executor recommendTaskExecutor;

    public RecommendAsyncTaskService(RecommendGatewayService recommendGatewayService,
                                     UserProfileService userProfileService,
                                     StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper,
                                     BridgeWorkRecommendProperties recommendProperties,
                                     @Qualifier("recommendTaskExecutor") Executor recommendTaskExecutor) {
        this.recommendGatewayService = recommendGatewayService;
        this.userProfileService = userProfileService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.recommendProperties = recommendProperties;
        this.recommendTaskExecutor = recommendTaskExecutor;
    }

    public RecommendAsyncResponseDto requestQuick(Long userId, RecommendRequestDto request) {
        return requestTask("quick", userId, request);
    }

    public RecommendAsyncResponseDto requestMap(Long userId, RecommendRequestDto request) {
        return requestTask("map", userId, request);
    }

    public RecommendAsyncResponseDto getTaskStatus(Long userId, String requestId) {
        RecommendTaskEnvelope envelope = readEnvelope(taskKey(requestId))
                .orElseThrow(() -> new RecommendDomainException(
                        "RECOMMEND_TASK_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "추천 요청 상태를 찾을 수 없습니다."
                ));

        if (!userId.equals(envelope.userId())) {
            throw new RecommendDomainException(
                    "RECOMMEND_TASK_FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "다른 사용자의 추천 요청 상태에는 접근할 수 없습니다."
            );
        }

        return toResponse(envelope);
    }

    private RecommendAsyncResponseDto requestTask(String requestType, Long userId, RecommendRequestDto request) {
        RecommendationKeyContext keyContext = buildKeyContext(userId, request);
        String requestId = buildRequestId(requestType, userId, keyContext);
        String taskKey = taskKey(requestId);
        String lockKey = lockKey(requestId);

        RecommendTaskEnvelope existingEnvelope = readEnvelope(taskKey).orElse(null);
        if (existingEnvelope != null && existingEnvelope.status() == RecommendTaskStatus.COMPLETED) {
            return toResponse(existingEnvelope);
        }

        if (existingEnvelope != null && existingEnvelope.status() == RecommendTaskStatus.PROCESSING) {
            boolean lockAlive = Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
            Duration processingAge = Duration.between(existingEnvelope.updatedAt(), OffsetDateTime.now(ZoneOffset.UTC));
            if (lockAlive || processingAge.compareTo(recommendProperties.getRequestTimeout().plusSeconds(30)) <= 0) {
                return toResponse(existingEnvelope);
            }
        }

        Duration cacheTtl = ttlUntilNextCacheBoundary();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(cacheTtl);
        Duration lockTtl = recommendProperties.getRequestTimeout().plusSeconds(30);

        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", lockTtl);
        if (!Boolean.TRUE.equals(acquired)) {
            if (existingEnvelope != null) {
                return toResponse(existingEnvelope.withUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC)));
            }

            RecommendTaskEnvelope processingEnvelope = RecommendTaskEnvelope.processing(
                    requestId,
                    requestType,
                    userId,
                    expiresAt,
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            writeEnvelope(taskKey, processingEnvelope, cacheTtl);
            return toResponse(processingEnvelope);
        }

        RecommendTaskEnvelope processingEnvelope = RecommendTaskEnvelope.processing(
                requestId,
                requestType,
                userId,
                expiresAt,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        writeEnvelope(taskKey, processingEnvelope, cacheTtl);

        recommendTaskExecutor.execute(() -> {
            try {
                Map<String, Object> result = "quick".equals(requestType)
                        ? recommendGatewayService.recommendQuick(userId, request)
                        : recommendGatewayService.recommendMap(userId, request);

                RecommendTaskEnvelope completedEnvelope = processingEnvelope.complete(result, OffsetDateTime.now(ZoneOffset.UTC));
                writeEnvelope(taskKey, completedEnvelope, cacheTtl);
            } catch (Exception exception) {
                RecommendTaskEnvelope failedEnvelope = processingEnvelope.fail(
                        resolveErrorMessage(exception),
                        OffsetDateTime.now(ZoneOffset.UTC)
                );
                writeEnvelope(taskKey, failedEnvelope, cacheTtl);
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        });

        return toResponse(processingEnvelope);
    }

    private RecommendationKeyContext buildKeyContext(Long userId, RecommendRequestDto request) {
        boolean aiEnabled = request == null || request.useAi();
        if (!aiEnabled) {
            return new RecommendationKeyContext(false, null, null);
        }

        UserProfileResponseDto profile;
        if (request != null && request.profileId() != null) {
            profile = userProfileService.getProfile(userId, request.profileId());
        } else {
            List<UserProfileResponseDto> profiles = userProfileService.getProfiles(userId);
            if (profiles.isEmpty()) {
                throw new RecommendDomainException(
                        "PROFILE_REQUIRED",
                        HttpStatus.BAD_REQUEST,
                        "AI 추천을 사용하려면 프로필이 필요합니다."
                );
            }
            profile = profiles.get(0);
        }

        return new RecommendationKeyContext(
                true,
                profile.profileId(),
                profile.updatedAt()
        );
    }

    private String buildRequestId(String requestType, Long userId, RecommendationKeyContext keyContext) {
        String signature = String.join("|",
                requestType,
                String.valueOf(userId),
                keyContext.aiEnabled() ? "ai" : "basic",
                String.valueOf(keyContext.profileId() == null ? 0L : keyContext.profileId()),
                String.valueOf(keyContext.profileUpdatedAt() == null ? 0L : keyContext.profileUpdatedAt().toInstant().toEpochMilli())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (Exception exception) {
            throw new RecommendDomainException(
                    "RECOMMEND_TASK_ID_BUILD_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "추천 요청 식별자 생성에 실패했습니다.",
                    exception
            );
        }
    }

    private Duration ttlUntilNextCacheBoundary() {
        return ttlUntilNextCacheBoundary(LocalDateTime.now(SEOUL_ZONE));
    }

    static Duration ttlUntilNextCacheBoundary(LocalDateTime now) {
        LocalDateTime boundary = now.withHour(DAILY_CACHE_BOUNDARY_HOUR).withMinute(0).withSecond(0).withNano(0);
        if (!now.isBefore(boundary)) {
            boundary = boundary.plusDays(1);
        }

        Duration ttl = Duration.between(now, boundary);
        if (ttl.isNegative() || ttl.isZero()) {
            return Duration.ofMinutes(1);
        }
        return ttl;
    }

    private String taskKey(String requestId) {
        return TASK_KEY_PREFIX + requestId;
    }

    private String lockKey(String requestId) {
        return TASK_LOCK_KEY_PREFIX + requestId;
    }

    private Optional<RecommendTaskEnvelope> readEnvelope(String taskKey) {
        String raw = stringRedisTemplate.opsForValue().get(taskKey);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, RecommendTaskEnvelope.class));
        } catch (JsonProcessingException exception) {
            throw new RecommendDomainException(
                    "RECOMMEND_TASK_CACHE_READ_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "추천 상태 캐시 해석에 실패했습니다.",
                    exception
            );
        }
    }

    private void writeEnvelope(String taskKey, RecommendTaskEnvelope envelope, Duration ttl) {
        try {
            String serialized = objectMapper.writeValueAsString(envelope);
            stringRedisTemplate.opsForValue().set(taskKey, serialized, ttl);
        } catch (JsonProcessingException exception) {
            throw new RecommendDomainException(
                    "RECOMMEND_TASK_CACHE_WRITE_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "추천 상태 캐시 저장에 실패했습니다.",
                    exception
            );
        }
    }

    private RecommendAsyncResponseDto toResponse(RecommendTaskEnvelope envelope) {
        return new RecommendAsyncResponseDto(
                envelope.requestId(),
                envelope.requestType(),
                envelope.status(),
                envelope.result(),
                envelope.errorMessage(),
                envelope.expiresAt(),
                envelope.updatedAt()
        );
    }

    private String resolveErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "추천 계산 중 오류가 발생했습니다.";
        }
        return message;
    }

    private record RecommendationKeyContext(
            boolean aiEnabled,
            Long profileId,
            OffsetDateTime profileUpdatedAt
    ) {
    }

    public static record RecommendTaskEnvelope(
            String requestId,
            String requestType,
            Long userId,
            RecommendTaskStatus status,
            Map<String, Object> result,
            String errorMessage,
            OffsetDateTime expiresAt,
            OffsetDateTime updatedAt
    ) {
        static RecommendTaskEnvelope processing(String requestId,
                                               String requestType,
                                               Long userId,
                                               OffsetDateTime expiresAt,
                                               OffsetDateTime updatedAt) {
            return new RecommendTaskEnvelope(
                    requestId,
                    requestType,
                    userId,
                    RecommendTaskStatus.PROCESSING,
                    null,
                    null,
                    expiresAt,
                    updatedAt
            );
        }

        RecommendTaskEnvelope complete(Map<String, Object> result, OffsetDateTime updatedAt) {
            return new RecommendTaskEnvelope(
                    requestId,
                    requestType,
                    userId,
                    RecommendTaskStatus.COMPLETED,
                    result,
                    null,
                    expiresAt,
                    updatedAt
            );
        }

        RecommendTaskEnvelope fail(String errorMessage, OffsetDateTime updatedAt) {
            return new RecommendTaskEnvelope(
                    requestId,
                    requestType,
                    userId,
                    RecommendTaskStatus.FAILED,
                    null,
                    errorMessage,
                    expiresAt,
                    updatedAt
            );
        }

        RecommendTaskEnvelope withUpdatedAt(OffsetDateTime updatedAt) {
            return new RecommendTaskEnvelope(
                    requestId,
                    requestType,
                    userId,
                    status,
                    result,
                    errorMessage,
                    expiresAt,
                    updatedAt
            );
        }
    }
}
