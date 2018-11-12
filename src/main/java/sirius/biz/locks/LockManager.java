/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.kernel.di.std.Named;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

/**
 * Provides an actual implementation to manage locks.
 */
public interface LockManager extends Named {

    /**
     * Tries to acquire the lock with the given name within the given interval.
     *
     * @param lockName       the name of the lock to acquire.
     * @param acquireTimeout the max time to wait for a lock. Used <tt>null</tt> to immediatelly return if a lock
     *                       cannot be obtained.
     * @return <tt>true</tt> if the lock was acquired, <tt>false</tt> otherwise
     */
    boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout);

    /**
     * Tries to acquire the lock with the given name within the given interval.
     *
     * @param lockName       the name of the lock to acquire.
     * @param acquireTimeout the max time to wait for a lock. Used <tt>null</tt> to immediatelly return if a lock
     *                       cannot be obtained.
     * @param lockTimeout    the max duration for which the lock will be kept before auto-releasing it.
     * @return <tt>true</tt> if the lock was acquired, <tt>false</tt> otherwise
     */
    boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout, @Nonnull Duration lockTimeout);

    /**
     * Determines if the lock is currently being locked.
     *
     * @param lock the name of the lock to check
     * @return <tt>true</tt> if the lock is locked, <tt>false</tt> otherwise
     */
    boolean isLocked(@Nonnull String lock);

    /**
     * Unlocks the given lock.
     *
     * @param lock  the name of the lock
     * @param force <tt>true</tt> if the lock should be removed, even if it is acquired by another node
     */
    void unlock(String lock, boolean force);

    /**
     * Returns the list of known locks.
     *
     * @return a list of all available locks
     */
    List<LockInfo> getLocks();
}
