/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.kernel.commons.Wait;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;

/**
 * Provides a basic implementation which tries to obtain a lock in increasing intervals.
 */
public abstract class BasicLockManager implements LockManager {

    @Override
    public boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout) {
        try {
            long timeout = acquireTimeout == null ? 0 : Instant.now().plus(acquireTimeout).toEpochMilli();
            int waitInMillis = getInitialWait();
            do {
                if (acquireLock(lockName)) {
                    return true;
                }
                Wait.millis(waitInMillis);
                waitInMillis = Math.min(getMaxWait(), waitInMillis + getWaitIncrement());
            } while (System.currentTimeMillis() < timeout);
            return false;
        } catch (Exception exception) {
            Exceptions.handle(Locks.LOG, exception);
            return false;
        }
    }

    @Override
    public boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout, @Nonnull Duration lockTimeout) {
        // Ignore lock timeout as its not supported here
        return tryLock(lockName, acquireTimeout);
    }

    /**
     * If the lock is already aquired, this returns the initial amount of milliseconds to wait.
     *
     * @return the duration of the first interval until a new attempt is made to acquire a lock
     */
    protected abstract int getInitialWait();

    /**
     * If the lock is still acquired, this returns the amount of milliseconds to increase the wait interval.
     *
     * @return the number of milliseconds to add to the current wait interval until a new attempt is made to acquire a
     * lock
     */
    protected abstract int getWaitIncrement();

    /**
     * Returns the maximal interval in millis which is spent until a new attempt is made to acquire a lock.
     *
     * @return the max interval until a new attempt is made to acquire a lock
     */
    protected abstract int getMaxWait();

    /**
     * Actually obtains a lock and returns immediatelly.
     *
     * @param lockName the name of the lock to acquire.
     * @return <tt>true</tt> if the lock was obtained, <tt>false</tt> otherwise
     */
    protected abstract boolean acquireLock(String lockName);
}
