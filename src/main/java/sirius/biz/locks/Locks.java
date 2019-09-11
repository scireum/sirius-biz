/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricsCollector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a central framework to obtain and manage named locks.
 * <p>
 * These locks can either be distributed (via SQL or REDIS) or held locally. The implementation is provided via a
 * {@link LockManager}.
 * <p>
 * Note that the implementation provided by the {@link LockManager} are not guaranteed to be reentrant. Therefore
 * we keep a local map <tt>"localLocks"</tt> (lock name to thread id) to simulate this behaviour.
 */
@Register(classes = {Locks.class, MetricProvider.class}, framework = Locks.FRAMEWORK_LOCKS)
public class Locks implements MetricProvider {

    /**
     * Names the framework which must be enabled to activate the locks feature.
     */
    public static final String FRAMEWORK_LOCKS = "biz.locks";

    private static final Duration LONG_RUNNING_LOGS_THRESHOLD = Duration.ofMinutes(30);

    public static final Log LOG = Log.get("locks");

    @Part(configPath = "locks.manager")
    private LockManager manager;

    /**
     * Contains a map of locally held locks (name to thread id).
     * <p>
     * As all mutators are in a synchronized method, the doesn't need to be thread safe.
     */
    private Map<String, Tuple<Long, AtomicInteger>> localLocks = new HashMap<>();

    /**
     * Tries to acquire the given lock in the given timeslot.
     * <p>
     * A sane value for the timeout might be in the range of 5-50s, highly depending on the algorithm
     * being protected by the lock. If the value is <tt>null</tt>, no retries will be performed.
     *
     * @param lockName       the name of the lock to acquire
     * @param acquireTimeout the max duration during which retires will be performed
     * @return <tt>true</tt> if the lock was acquired, <tt>false</tt> otherwise
     */
    public boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout) {
        if (getLock(lockName) != null) {
            return true;
        }

        if (manager.tryLock(lockName, acquireTimeout)) {
            localLocks.put(lockName, Tuple.create(Thread.currentThread().getId(), new AtomicInteger(1)));
            return true;
        }

        return false;
    }

    /**
     * Tries to acquire the given lock in the given timeslot.
     * <p>
     * A sane value for the timeout might be in the range of 5-50s, highly depending on the algorithm
     * being protected by the lock. If the value is <tt>null</tt>, no retries will be performed.
     *
     * @param lockName       the name of the lock to acquire
     * @param acquireTimeout the max duration during which retires will be performed
     * @param lockTimeout    the max duration for which the lock will be kept before auto-releasing it
     * @return <tt>true</tt> if the lock was acquired, <tt>false</tt> otherwise
     */
    public boolean tryLock(@Nonnull String lockName,
                           @Nullable Duration acquireTimeout,
                           @Nonnull Duration lockTimeout) {
        if (getLock(lockName) != null) {
            return true;
        }

        if (manager.tryLock(lockName, acquireTimeout, lockTimeout)) {
            localLocks.put(lockName, Tuple.create(Thread.currentThread().getId(), new AtomicInteger(1)));
            return true;
        }

        return false;
    }

    private synchronized Tuple<Long, AtomicInteger> getLock(String lockName) {
        Long currentThreadId = Thread.currentThread().getId();
        Tuple<Long, AtomicInteger> localLockInfo = localLocks.get(lockName);

        if (localLockInfo != null && Objects.equals(currentThreadId, localLockInfo.getFirst())) {
            // Already locked by this thread - increment the nesting level to handle the unlock
            // calls properly...
            localLockInfo.getSecond().incrementAndGet();
            return localLockInfo;
        }

        return null;
    }

    /**
     * Boilerplate method to perform the given task while holding the given lock.
     * <p>
     * See {@link #tryLock(String, Duration)} for details on acquiring a lock.
     * <p>
     * If the lock cannot be acquired, nothing will happen (neighter the task will be execute nor an exception will be
     * thrown).
     *
     * @param lock           the name of the lock to acquire
     * @param acquireTimeout the max duration during which retires will be performed
     * @param lockedTask     the task to execute while holding the given lock. The task will not be executed if the
     *                       lock cannot be acquired within the given period
     */
    public void tryLocked(@Nonnull String lock, @Nullable Duration acquireTimeout, @Nonnull Runnable lockedTask) {
        if (tryLock(lock, acquireTimeout)) {
            try {
                lockedTask.run();
            } finally {
                unlock(lock);
            }
        }
    }

    /**
     * Determines if the given lock is currently locked.
     *
     * @param lock the lock to check
     * @return <tt>true</tt> if the lock is currently active, <tt>false</tt> otherwise
     */
    public synchronized boolean isLocked(@Nonnull String lock) {
        Tuple<Long, AtomicInteger> localLockInfo = localLocks.get(lock);

        if (localLockInfo != null) {
            return true;
        }

        return manager.isLocked(lock);
    }


    /**
     * Determines if the given lock is currently locked by the current thread.
     *
     * @param lock the lock to check
     * @return <tt>true</tt> if the lock is currently active, <tt>false</tt> otherwise
     */
    public synchronized boolean isLockedByCurrentThread(@Nonnull String lock) {
        Long currentThreadId = Thread.currentThread().getId();
        Tuple<Long, AtomicInteger> localLockInfo = localLocks.get(lock);

        if (localLockInfo != null) {
            return Objects.equals(currentThreadId, localLockInfo.getFirst());
        }

        return manager.isLocked(lock);
    }

    /**
     * Releases the lock.
     *
     * @param lock the lock to release
     */
    public void unlock(String lock) {
        unlock(lock, false);
    }

    /**
     * Releases the given lock.
     *
     * @param lock  the lock to release
     * @param force if <tt>true</tt>, the lock will even be released if it is held by another node. This is a very
     *              dangerous operation and should only be used by maintenance and management tools.
     */
    public synchronized void unlock(String lock, boolean force) {
        if (!force) {
            if (unlockLocally(lock)) {
                return;
            }
        }

        manager.unlock(lock, force);
        localLocks.remove(lock);
    }

    /**
     * Determines if the lock is held by the appropriate thread and chckes the nesting level.
     *
     * @param lock the lock to check
     * @return <tt>true</tt> if the unlock was local due to several nested locks or <tt>false</tt> if the lock
     * should be globally unlocked.
     */
    private boolean unlockLocally(String lock) {
        Long currentThreadId = Thread.currentThread().getId();
        Tuple<Long, AtomicInteger> localLockInfo = localLocks.get(lock);

        if (localLockInfo == null) {
            // Not locked by any global thead. Let the LockManager handle this error...
            return false;
        }

        if (Objects.equals(currentThreadId, localLockInfo.getFirst())) {
            // Determine the local nesting level...
            return localLockInfo.getSecond().decrementAndGet() >= 1;
        } else {
            // Locked by another thread - abort...
            LOG.WARN("Not going to unlock '%s' for thread '%s' as it was locked by thread %s",
                     lock,
                     currentThreadId,
                     localLockInfo.getFirst());
            return true;
        }
    }

    @Override
    public void gather(MetricsCollector collector) {
        List<LockInfo> locks = getLocks();
        LocalDateTime limitForAcquired = LocalDateTime.now().minus(LONG_RUNNING_LOGS_THRESHOLD);

        collector.metric("locks_count", "locks-count", "Active Locks", locks.size(), null);
        collector.metric("locks_long_running",
                         "locks-long-running",
                         "Long locks",
                         locks.stream().filter(l -> l.getAcquired().isBefore(limitForAcquired)).count(),
                         null);
    }

    /**
     * Returns a list of all currently held locks.
     *
     * @return a list of all locks
     */
    public List<LockInfo> getLocks() {
        return manager.getLocks();
    }
}
