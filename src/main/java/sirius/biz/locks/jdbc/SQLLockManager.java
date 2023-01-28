/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks.jdbc;

import sirius.biz.locks.BasicLockManager;
import sirius.biz.locks.LockInfo;
import sirius.biz.locks.LockManager;
import sirius.biz.locks.Locks;
import sirius.db.jdbc.DeleteStatement;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.Mixing;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Provides distributed locks based on SQL.
 */
@Register(classes = LockManager.class, framework = Locks.FRAMEWORK_LOCKS)
public class SQLLockManager extends BasicLockManager {

    @Part
    private OMA oma;

    @Part
    private Mixing mixing;

    @Nonnull
    @Override
    public String getName() {
        return "sql";
    }

    @Override
    protected int getMaxWait() {
        return 1500;
    }

    @Override
    protected int getWaitIncrement() {
        return 500;
    }

    @Override
    protected int getInitialWait() {
        return 500;
    }

    private void awaitReadiness() {
        if (!oma.isReady()) {
            oma.getReadyFuture().await(Duration.ofSeconds(30));
        }
    }

    @Override
    protected boolean acquireLock(@Nonnull String lockName) {
        try {
            awaitReadiness();

            oma.getDatabase(Mixing.DEFAULT_REALM)
               .insertRow(mixing.getDescriptor(ManagedLock.class).getRelationName(),
                          Context.create()
                                 .set(ManagedLock.NAME.getName(), lockName)
                                 .set(ManagedLock.OWNER.getName(), CallContext.getNodeName())
                                 .set(ManagedLock.THREAD.getName(), Thread.currentThread().getName())
                                 .set(ManagedLock.ACQUIRED.getName(), Instant.now().toEpochMilli()));
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            // Lock is locked - retry if possible :-(
            Exceptions.ignore(e);
            return false;
        } catch (SQLException e) {
            throw Exceptions.handle(Locks.LOG, e);
        }
    }

    @Override
    public boolean isLocked(@Nonnull String lock) {
        awaitReadiness();
        return oma.select(ManagedLock.class).eq(ManagedLock.NAME, lock).exists();
    }

    @Override
    public void unlock(String lock, boolean force) {
        awaitReadiness();

        try {
            DeleteStatement deleteStatement = oma.deleteStatement(ManagedLock.class).where(ManagedLock.NAME, lock);
            if (!force) {
                deleteStatement.where(ManagedLock.OWNER, CallContext.getNodeName());
            }

            deleteStatement.executeUpdate();
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(OMA.LOG)
                            .withSystemErrorMessage("An error occurred while unlocking %s: %s - %s", lock)
                            .handle();
        }
    }

    @Override
    public List<LockInfo> getLocks() {
        awaitReadiness();

        return oma.select(ManagedLock.class).queryList().stream().map(this::transformLock).toList();
    }

    private LockInfo transformLock(ManagedLock managedLock) {
        return new LockInfo(managedLock.getName(),
                            managedLock.getOwner(),
                            managedLock.getThread(),
                            managedLock.getAcquired());
    }
}
