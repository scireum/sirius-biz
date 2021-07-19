/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a fast implementation which is based on internal JVM locks.
 * <p>
 * Note that this managed is not suitable for clusters, as locks are only held locally.
 */
@Register(classes = LockManager.class, framework = Locks.FRAMEWORK_LOCKS)
public class JavaLockManager extends BasicLockManager {
    /**
     * Contains the name of this lock manager
     */
    public static final String NAME = "java";

    private final Map<String, LockInfo> locks = new HashMap<>();

    @Nonnull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected int getMaxWait() {
        return 100;
    }

    @Override
    protected int getWaitIncrement() {
        return 10;
    }

    @Override
    protected int getInitialWait() {
        return 10;
    }

    @Override
    protected synchronized boolean acquireLock(String lockName) {
        if (locks.containsKey(lockName)) {
            return false;
        }

        locks.put(lockName, new LockInfo(lockName, "(local)", Thread.currentThread().getName(), LocalDateTime.now()));

        return true;
    }

    @Override
    public synchronized boolean isLocked(@Nonnull String lock) {
        return locks.containsKey(lock);
    }

    @Override
    public synchronized void unlock(String lock, boolean force) {
        locks.remove(lock);
    }

    @Override
    public synchronized List<LockInfo> getLocks() {
        return new ArrayList<>(locks.values());
    }
}
