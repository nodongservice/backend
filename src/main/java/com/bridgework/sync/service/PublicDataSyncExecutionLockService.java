package com.bridgework.sync.service;

import com.bridgework.sync.exception.SyncInProgressException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.stereotype.Service;

@Service
public class PublicDataSyncExecutionLockService {

    private static final String LOCK_NAME = "publicDataSyncExecution";
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(30);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ZERO;

    private final LockProvider lockProvider;

    public PublicDataSyncExecutionLockService(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }

    public <T> T runManualOrThrow(Supplier<T> task) {
        Optional<SimpleLock> lock = tryAcquire();
        if (lock.isEmpty()) {
            throw new SyncInProgressException();
        }
        try {
            return task.get();
        } finally {
            lock.get().unlock();
        }
    }

    public boolean runSchedulerIfAvailable(Runnable task) {
        Optional<SimpleLock> lock = tryAcquire();
        if (lock.isEmpty()) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            lock.get().unlock();
        }
    }

    private Optional<SimpleLock> tryAcquire() {
        LockConfiguration lockConfiguration = new LockConfiguration(
                Instant.now(),
                LOCK_NAME,
                LOCK_AT_MOST_FOR,
                LOCK_AT_LEAST_FOR
        );
        return lockProvider.lock(lockConfiguration);
    }
}

