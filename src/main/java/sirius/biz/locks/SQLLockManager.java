/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.db.mixing.OMA;
import sirius.db.mixing.Schema;
import sirius.db.mixing.constraints.FieldOperator;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides distributed locks based on SQL.
 */
@Framework("biz.locks")
@Register(classes = LockManager.class)
public class SQLLockManager extends BasicLockManager {

    @Part
    private OMA oma;

    @Part
    private Schema schema;

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

    @Override
    protected boolean acquireLock(@Nonnull String lockName) {
        try {
            oma.getDatabase()
               .insertRow(schema.getDescriptor(ManagedLock.class).getTableName(),
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
        return oma.select(ManagedLock.class).where(FieldOperator.on(ManagedLock.NAME).eq(lock)).exists();
    }

    @Override
    public void unlock(String lock, boolean force) {
        try {
            if (force) {
                oma.getDatabase()
                   .createQuery("DELETE FROM managedlock WHERE name = ${name}")
                   .set("name", lock)
                   .executeUpdate();
            } else {
                oma.getDatabase()
                   .createQuery("DELETE FROM managedlock WHERE name = ${name} AND owner = ${owner}")
                   .set("name", lock)
                   .set("owner", CallContext.getNodeName())
                   .executeUpdate();
            }
        } catch (SQLException e) {
            Exceptions.handle(Locks.LOG, e);
        }
    }

    @Override
    public List<LockInfo> getLocks() {
        return oma.select(ManagedLock.class).queryList().stream().map(this::transformLock).collect(Collectors.toList());
    }

    private LockInfo transformLock(ManagedLock managedLock) {
        return new LockInfo(managedLock.getName(),
                            managedLock.getOwner(),
                            managedLock.getThread(),
                            managedLock.getAcquired());
    }
}
