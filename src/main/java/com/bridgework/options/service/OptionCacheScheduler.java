package com.bridgework.options.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OptionCacheScheduler {

    private static final Logger log = LoggerFactory.getLogger(OptionCacheScheduler.class);
    private static final String JOB_CATEGORY_TREE_CACHE = "jobCategoryTree";

    private final CacheManager cacheManager;

    public OptionCacheScheduler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // 00시 동기화 이후 데이터 안정화 시간을 고려해 매일 02:00(KST)에 캐시를 비운다.
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void evictJobCategoryTreeCache() {
        Cache cache = cacheManager.getCache(JOB_CATEGORY_TREE_CACHE);
        if (cache == null) {
            log.warn("직무 트리 캐시가 초기화되지 않아 캐시 비우기를 건너뜁니다. cacheName={}", JOB_CATEGORY_TREE_CACHE);
            return;
        }
        cache.clear();
        log.info("직무 트리 캐시를 비웠습니다. cacheName={}", JOB_CATEGORY_TREE_CACHE);
    }
}
