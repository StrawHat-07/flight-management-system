package com.flightmanagement.flight.service.lock;

import java.util.List;
import java.util.function.Supplier;

/**
 * Interface for distributed lock operations.
 * Allows for different lock implementations (Redis, ZooKeeper, in-memory for testing).
 */
public interface LockOperations {

    /**
     * Executes action with lock on single resource.
     *
     * @param resourceId Resource to lock
     * @param action Action to execute while holding lock
     * @return Result of action
     * @throws LockAcquisitionException if lock cannot be acquired
     */
    <T> T executeWithLock(String resourceId, Supplier<T> action);

    /**
     * Executes action with locks on multiple resources.
     * Acquires locks in sorted order to prevent deadlocks.
     *
     * @param resourceIds Resources to lock
     * @param action Action to execute while holding locks
     * @return Result of action
     * @throws LockAcquisitionException if locks cannot be acquired
     */
    <T> T executeWithMultiLock(List<String> resourceIds, Supplier<T> action);

    class LockAcquisitionException extends RuntimeException {
        public LockAcquisitionException(String message) {
            super(message);
        }
    }
}
