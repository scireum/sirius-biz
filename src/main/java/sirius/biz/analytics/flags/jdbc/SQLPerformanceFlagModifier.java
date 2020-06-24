/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags.jdbc;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.flags.PerformanceFlagModifier;
import sirius.biz.analytics.flags.PerformanceFlagged;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntity;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.sql.SQLException;

/**
 * Provides the SQL/JDBC implementation to modify flags stored in {@link SQLPerformanceData}.
 */
public class SQLPerformanceFlagModifier implements PerformanceFlagModifier {

    private SQLPerformanceData target;
    private long originalFlags;

    @Part
    private static OMA oma;

    protected SQLPerformanceFlagModifier(SQLPerformanceData target) {
        this.target = target;
        this.originalFlags = target.flags;
    }

    @Override
    public PerformanceFlagModifier set(PerformanceFlag flag, boolean targetValue) {
        if (targetValue) {
            set(flag);
        } else {
            clear(flag);
        }

        return this;
    }

    @Override
    public PerformanceFlagModifier set(PerformanceFlag flag) {
        target.flags |= 1L << flag.getBitIndex();
        return this;
    }

    @Override
    public PerformanceFlagModifier clear(PerformanceFlag flag) {
        target.flags &= ~(1L << flag.getBitIndex());
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void commit() {
        try {
            if (target.flags == originalFlags) {
                return;
            }

            oma.updateStatement((Class<? extends SQLEntity>) target.getOwner().getClass())
               .set(PerformanceFlagged.PERFORMANCE_DATA.inner(SQLPerformanceData.FLAGS), target.flags)
               .where(SQLEntity.ID, target.getOwner().getId())
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(Log.BACKGROUND)
                      .error(e)
                      .withSystemErrorMessage("Failed to update performance flags of %s (%s): %s (%s)",
                                              target.getOwner().getIdAsString(),
                                              target.getOwner().getClass().getName())
                      .handle();
        }
    }
}
