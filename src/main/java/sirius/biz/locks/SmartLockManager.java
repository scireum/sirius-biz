/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.db.redis.Redis;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

/**
 * Provides a "cluster aware" lock manager.
 * <p>
 * Uses {@link RedisLockManager} is <tt>Redis</tt> is configured or otherwise {@link JavaLockManager}
 */
@Register(classes = LockManager.class, framework = Locks.FRAMEWORK_LOCKS)
public class SmartLockManager implements LockManager {

    @Part
    private GlobalContext ctx;

    @Part
    private Redis redis;

    private LockManager delegate;

    @Nonnull
    @Override
    public String getName() {
        return "smart";
    }

    private LockManager getDelegate() {
        if (delegate == null) {
            determineLockManager();
        }

        return delegate;
    }

    private void determineLockManager() {
        if (redis.isConfigured()) {
            Locks.LOG.INFO("SmartLockManager: Using RedisLockManager as Redis is configured");
            delegate = ctx.getPart(RedisLockManager.NAME, LockManager.class);
        } else {
            Locks.LOG.INFO(
                    "SmartLockManager: Assuming a single machine setup as Redis isn't present - using fast JVM locks");
            delegate = ctx.getPart(JavaLockManager.NAME, LockManager.class);
        }
    }

    @Override
    public boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout) {
        return getDelegate().tryLock(lockName, acquireTimeout);
    }

    @Override
    public boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout, @Nonnull Duration lockTimeout) {
        return getDelegate().tryLock(lockName, acquireTimeout, lockTimeout);
    }

    @Override
    public boolean isLocked(@Nonnull String lock) {
        return getDelegate().isLocked(lock);
    }

    @Override
    public void unlock(String lock, boolean force) {
        getDelegate().unlock(lock, force);
    }

    @Override
    public List<LockInfo> getLocks() {
        return getDelegate().getLocks();
    }
}
