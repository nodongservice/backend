package com.bridgework.common.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiter {

    private static final String LUA_SCRIPT = """
            local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
            local updated_at = tonumber(redis.call('HGET', KEYS[1], 'updated_at'))
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_tokens = tonumber(ARGV[3])
            local refill_period_ms = tonumber(ARGV[4])

            if tokens == nil then
              tokens = capacity
              updated_at = now
            end

            local elapsed = math.max(0, now - updated_at)
            local refill = (elapsed / refill_period_ms) * refill_tokens
            tokens = math.min(capacity, tokens + refill)

            local allowed = 0
            if tokens >= 1 then
              allowed = 1
              tokens = tokens - 1
            end

            local retry_after_ms = 0
            if allowed == 0 then
              retry_after_ms = math.ceil(((1 - tokens) / refill_tokens) * refill_period_ms)
            end

            local reset_after_ms = math.ceil(((capacity - tokens) / refill_tokens) * refill_period_ms)
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'updated_at', now)
            redis.call('PEXPIRE', KEYS[1], math.max(refill_period_ms, reset_after_ms) * 2)

            return { allowed, capacity, math.floor(tokens), math.ceil(retry_after_ms / 1000), math.ceil(reset_after_ms / 1000) }
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> redisScript;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    public RateLimitResult consume(String key,
                                   long capacity,
                                   long refillTokens,
                                   Duration refillPeriod) {
        List<?> result = redisTemplate.execute(
                redisScript,
                List.of(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(Math.max(1, capacity)),
                String.valueOf(Math.max(1, refillTokens)),
                String.valueOf(Math.max(1, refillPeriod.toMillis()))
        );

        if (result == null || result.size() < 5) {
            throw new IllegalStateException("Rate limit result is empty.");
        }

        return new RateLimitResult(
                toLong(result.get(0)) == 1,
                toLong(result.get(1)),
                toLong(result.get(2)),
                toLong(result.get(3)),
                toLong(result.get(4))
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
