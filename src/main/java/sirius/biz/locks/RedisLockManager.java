/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.db.redis.Redis;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

/**
 * Provides a fast multi node implementation which is based on Redis.
 */
@Register(framework = Locks.FRAMEWORK_LOCKS)
public class RedisLockManager implements LockManager {

    /**
     * Contains the name of this lock manager
     */
    public static final String NAME = "redis";

    @Part
    private Redis redis;

    @Nonnull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout) {
        return tryLock(lockName, acquireTimeout, Duration.ofMinutes(30));
    }

    @Override
    public boolean tryLock(@Nonnull String lockName, @Nullable Duration acquireTimeout, @Nonnull Duration lockTimeout) {
        return redis.tryLock(lockName, acquireTimeout, lockTimeout);
    }

    @Override
    public boolean isLocked(@Nonnull String lock) {
        return redis.isLocked(lock);
    }

    @Override
    public void unlock(String lock, boolean force) {
        redis.unlock(lock, force);
    }

    @Override
    public List<LockInfo> getLocks() {
        return redis.getLockList().stream().map(this::transformLockInfo).toList();
    }

    private LockInfo transformLockInfo(Redis.LockInfo redisLock) {
        return new LockInfo(redisLock.name, redisLock.value, "-", redisLock.since);
    }
}
