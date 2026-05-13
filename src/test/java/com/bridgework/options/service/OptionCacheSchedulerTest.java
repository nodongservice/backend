package com.bridgework.options.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.scheduling.annotation.Scheduled;

class OptionCacheSchedulerTest {

    @Test
    void evictJobCategoryTreeCache_clearsJobCategoryTreeCache() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("jobCategoryTree");
        Cache cache = cacheManager.getCache("jobCategoryTree");
        cache.put("tree", "cached");

        new OptionCacheScheduler(cacheManager).evictJobCategoryTreeCache();

        assertThat(cache.get("tree")).isNull();
    }

    @Test
    void evictJobCategoryTreeCache_runsDailyAtTwoAmKst() throws NoSuchMethodException {
        Method method = OptionCacheScheduler.class.getMethod("evictJobCategoryTreeCache");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 2 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}
