package com.flightmanagement.flight.service;

import com.flightmanagement.flight.service.lock.LockOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService implements LockOperations {

    private static final String LOCK_KEY_PREFIX = "lock:flight:";
    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final long RETRY_DELAY_MS = 50;

    private final StringRedisTemplate stringRedisTemplate;

    private static final String RELEASE_LOCK_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "return redis.call('DEL', KEYS[1]) " +
            "else return 0 end";

    public LockHandle acquireLock(String flightId) {
        return acquireLock(flightId, DEFAULT_LOCK_TIMEOUT, DEFAULT_WAIT_TIMEOUT);
    }

    public LockHandle acquireLock(String flightId, Duration lockTimeout, Duration waitTimeout) {
        String lockKey = LOCK_KEY_PREFIX + flightId;
        String lockValue = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + waitTimeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, lockTimeout.toMillis(), TimeUnit.MILLISECONDS);

            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Acquired lock: flightId={}, lockKey={}", flightId, lockKey);
                return new LockHandle(lockKey, lockValue, true);
            }

            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Lock acquisition interrupted: flightId={}", flightId);
                return new LockHandle(lockKey, lockValue, false);
            }
        }

        log.warn("Failed to acquire lock within timeout: flightId={}, waitTimeout={}ms",
                flightId, waitTimeout.toMillis());
        return new LockHandle(lockKey, lockValue, false);
    }

    public LockHandle acquireMultiLock(List<String> flightIds) {
        return acquireMultiLock(flightIds, DEFAULT_LOCK_TIMEOUT, DEFAULT_WAIT_TIMEOUT);
    }

    public LockHandle acquireMultiLock(List<String> flightIds, Duration lockTimeout, Duration waitTimeout) {
        List<String> sortedIds = flightIds.stream().sorted().toList();

        for (String flightId : sortedIds) {
            LockHandle handle = acquireLock(flightId, lockTimeout, waitTimeout);
            if (!handle.isAcquired()) {
                releaseMultiLock(sortedIds.subList(0, sortedIds.indexOf(flightId)));
                return new LockHandle(null, null, false);
            }
        }

        String combinedKey = String.join(",", sortedIds);
        String combinedValue = UUID.randomUUID().toString();
        return new LockHandle(combinedKey, combinedValue, true, sortedIds);
    }

    public void releaseLock(LockHandle handle) {
        if (handle == null || !handle.isAcquired()) {
            return;
        }

        if (handle.getFlightIds() != null && !handle.getFlightIds().isEmpty()) {
            releaseMultiLock(handle.getFlightIds());
            return;
        }

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(script,
                Collections.singletonList(handle.getLockKey()),
                handle.getLockValue());

        if (result != null && result == 1L) {
            log.debug("Released lock: lockKey={}", handle.getLockKey());
        } else {
            log.warn("Lock release failed (expired or stolen): lockKey={}", handle.getLockKey());
        }
    }

    private void releaseMultiLock(List<String> flightIds) {
        for (String flightId : flightIds) {
            String lockKey = LOCK_KEY_PREFIX + flightId;
            stringRedisTemplate.delete(lockKey);
            log.debug("Released multi-lock: flightId={}", flightId);
        }
    }

    @Override
    public <T> T executeWithLock(String flightId, Supplier<T> action) {
        LockHandle handle = acquireLock(flightId);
        if (!handle.isAcquired()) {
            throw new LockOperations.LockAcquisitionException("Failed to acquire lock for flight: " + flightId);
        }

        try {
            return action.get();
        } finally {
            releaseLock(handle);
        }
    }

    @Override
    public <T> T executeWithMultiLock(List<String> flightIds, Supplier<T> action) {
        LockHandle handle = acquireMultiLock(flightIds);
        if (!handle.isAcquired()) {
            throw new LockOperations.LockAcquisitionException("Failed to acquire locks for flights: " + flightIds);
        }

        try {
            return action.get();
        } finally {
            releaseLock(handle);
        }
    }

    @lombok.Value
    public static class LockHandle {
        String lockKey;
        String lockValue;
        boolean acquired;
        List<String> flightIds;

        public LockHandle(String lockKey, String lockValue, boolean acquired) {
            this(lockKey, lockValue, acquired, null);
        }

        public LockHandle(String lockKey, String lockValue, boolean acquired, List<String> flightIds) {
            this.lockKey = lockKey;
            this.lockValue = lockValue;
            this.acquired = acquired;
            this.flightIds = flightIds;
        }
    }
}
