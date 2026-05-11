package com.bridgework.common.ratelimit;

import com.bridgework.auth.security.UserPrincipal;
import com.bridgework.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private final BridgeWorkRateLimitProperties properties;
    private final RedisRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(BridgeWorkRateLimitProperties properties,
                           RedisRateLimiter rateLimiter,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        return properties.getExcludedPathPatterns()
                .stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        BridgeWorkRateLimitProperties.Policy policy = findPolicy(request);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = buildKey(request, policy);
        try {
            RateLimitResult result = rateLimiter.consume(
                    key,
                    policy.getCapacity(),
                    policy.getRefillTokens(),
                    policy.getRefillPeriod()
            );
            writeHeaders(response, result);

            if (!result.allowed()) {
                writeTooManyRequests(response, result);
                return;
            }
        } catch (RuntimeException exception) {
            if (!properties.isFailOpen()) {
                throw exception;
            }
            log.warn("Rate limit check failed. Falling back to fail-open mode. key={}", key, exception);
        }

        filterChain.doFilter(request, response);
    }

    private BridgeWorkRateLimitProperties.Policy findPolicy(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        return properties.getPolicies()
                .stream()
                .filter(policy -> matchesMethod(policy, method))
                .filter(policy -> matchesPath(policy, path))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesMethod(BridgeWorkRateLimitProperties.Policy policy, String method) {
        List<String> methods = policy.getMethods();
        if (methods.isEmpty()) {
            return true;
        }
        return methods.stream().anyMatch(configuredMethod -> configuredMethod.equalsIgnoreCase(method));
    }

    private boolean matchesPath(BridgeWorkRateLimitProperties.Policy policy, String path) {
        return policy.getPathPatterns()
                .stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String buildKey(HttpServletRequest request, BridgeWorkRateLimitProperties.Policy policy) {
        String subject = switch (policy.getKeyScope()) {
            case IP -> "ip:" + clientIp(request);
            case USER -> "user:" + currentUserId().orElse("anonymous");
            case USER_OR_IP -> currentUserId()
                    .map(userId -> "user:" + userId)
                    .orElseGet(() -> "ip:" + clientIp(request));
        };
        String rawKey = properties.getRedisKeyPrefix() + ":" + policy.getId() + ":" + subject;
        return properties.getRedisKeyPrefix() + ":" + policy.getId() + ":" + DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8));
    }

    private java.util.Optional<String> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return java.util.Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return java.util.Optional.of(String.valueOf(userPrincipal.getUserId()));
        }
        return java.util.Optional.empty();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader(X_REAL_IP);
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private void writeHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetAfterSeconds()));
        if (!result.allowed()) {
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        }
    }

    private void writeTooManyRequests(HttpServletResponse response, RateLimitResult result) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                "RATE_LIMIT_EXCEEDED",
                "요청이 너무 많습니다. " + Math.max(1, result.retryAfterSeconds()) + "초 후 다시 시도해주세요."
        ));
    }
}
