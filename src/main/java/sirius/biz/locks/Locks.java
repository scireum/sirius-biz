/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.db.mixing.OMA;
import sirius.db.mixing.constraints.FieldOperator;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricsCollector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Provides distributed locks based on SQL.
 * <p>
 * Supports named locks which will work in distributed environments without actually locking tables or rows. Note
 * however that acquiring a lock causes at last one SQL statement to be executed, therefore these locks are more
 * expensive than {@link java.util.concurrent.locks.ReentrantLock} or the like.
 */
@Framework("locks")
@Register(classes = {Locks.class, MetricProvider.class})
public class Locks implements MetricProvider {

    private static final Duration LONG_RUNNING_LOGS_THRESHOLD = Duration.ofMinutes(30);

    @Part
    private OMA oma;

    public static final Log LOG = Log.get("locks");

    /**
     * Tries to acquire the given lock in the given timeslot.
     * <p>
     * The system will try to acquire the given lock. If the lock is currently in use, it will retry
     * in 0.5 second intervals until either the lock is acquired or the <tt>acquireTimeout</tt> is over.
     * <p>
     * A sane value for the timeout might be in the range of 5-50s, highly depending on the algorithm
     * being protected by the lock. If the value is <tt>null</tt>, no retries will be performed.
     *
     * @param lockName       the name of the lock to acquire
     * @param acquireTimeout the max duration during which retires (in 1 second intervals) will be performed
     * @return <tt>true</tt> if the lock was acquired, <tt>false</tt> otherwise
     */
    public boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout) {
        try {
            long timeout = acquireTimeout == null ? 0 : Instant.now().plus(acquireTimeout).toEpochMilli();
            int waitInMillis = 500;
            do {
                try {
                    oma.select(Lock.class).queryList();
                    oma.getDatabase()
                       .insertRow("lock",
                                  Context.create()
                                         .set(Lock.NAME.getName(), lockName)
                                         .set(Lock.OWNER.getName(), CallContext.getNodeName())
                                         .set(Lock.THREAD.getName(), Thread.currentThread().getName())
                                         .set(Lock.ACQUIRED.getName(), Instant.now().toEpochMilli()));
                    return true;
                } catch (SQLIntegrityConstraintViolationException e) {
                    // Lock is locked - retry if possible :-(
                    Exceptions.ignore(e);
                } catch (SQLException e) {
                    throw Exceptions.handle(LOG, e);
                }
                Wait.millis(waitInMillis);
                waitInMillis = Math.min(1500, waitInMillis + 500);
            } while (System.currentTimeMillis() < timeout);
            return false;
        } catch (Throwable e) {
            Exceptions.handle(LOG, e);
            return false;
        }
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
     * @param acquireTimeout the max duration during which retires (in 1 second intervals) will be performed
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
     * Determines if the given lock is currently locked by this or another node.
     *
     * @param lock the lock to check
     * @return <tt>true</tt> if the lock is currently active, <tt>false</tt> otherwise
     */
    public boolean isLocked(@Nonnull String lock) {
        return oma.select(Lock.class).where(FieldOperator.on(Lock.NAME).eq(lock)).exists();
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
    public void unlock(String lock, boolean force) {
        try {
            if (force) {
                oma.getDatabase()
                   .createQuery("DELETE FROM lock WHERE name = ${name}")
                   .set("name", lock)
                   .executeUpdate();
            } else {
                oma.getDatabase()
                   .createQuery("DELETE FROM lock WHERE name = ${name} AND owner = ${owner}")
                   .set("name", lock)
                   .set("owner", CallContext.getNodeName())
                   .executeUpdate();
            }
        } catch (SQLException e) {
            Exceptions.handle(LOG, e);
        }
    }

    @Override
    public void gather(MetricsCollector collector) {
        long numberOfLocks = oma.select(Lock.class).count();
        long numberOfLongRunningLocks = oma.select(Lock.class)
                                           .where(FieldOperator.on(Lock.ACQUIRED)
                                                               .lessThan(LocalDateTime.now()
                                                                                      .minus(LONG_RUNNING_LOGS_THRESHOLD)))
                                           .count();

        collector.metric("locks-count", "Active Locks", numberOfLocks, null);
        collector.metric("locks-long-running", "Long locks", numberOfLongRunningLocks, null);
    }
}
