package com.bridgework.common.ratelimit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bridgework.rate-limit")
public class BridgeWorkRateLimitProperties {

    private boolean enabled = true;
    private boolean failOpen = true;
    private String redisKeyPrefix = "bridgework:rate-limit";
    private List<String> excludedPathPatterns = new ArrayList<>();
    private List<Policy> policies = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public List<String> getExcludedPathPatterns() {
        return excludedPathPatterns;
    }

    public void setExcludedPathPatterns(List<String> excludedPathPatterns) {
        this.excludedPathPatterns = excludedPathPatterns == null ? new ArrayList<>() : excludedPathPatterns;
    }

    public List<Policy> getPolicies() {
        return policies;
    }

    public void setPolicies(List<Policy> policies) {
        this.policies = policies == null ? new ArrayList<>() : policies;
    }

    public static class Policy {

        private String id;
        private List<String> methods = new ArrayList<>();
        private List<String> pathPatterns = new ArrayList<>();
        private KeyScope keyScope = KeyScope.USER_OR_IP;
        private long capacity = 60;
        private long refillTokens = 60;
        private Duration refillPeriod = Duration.ofMinutes(1);

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods == null ? new ArrayList<>() : methods;
        }

        public List<String> getPathPatterns() {
            return pathPatterns;
        }

        public void setPathPatterns(List<String> pathPatterns) {
            this.pathPatterns = pathPatterns == null ? new ArrayList<>() : pathPatterns;
        }

        public KeyScope getKeyScope() {
            return keyScope;
        }

        public void setKeyScope(KeyScope keyScope) {
            this.keyScope = keyScope == null ? KeyScope.USER_OR_IP : keyScope;
        }

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod == null ? Duration.ofMinutes(1) : refillPeriod;
        }
    }
}
