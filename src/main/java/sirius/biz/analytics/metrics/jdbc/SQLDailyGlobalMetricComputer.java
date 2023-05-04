/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics.jdbc;

import sirius.biz.analytics.metrics.DailyMetricComputer;
import sirius.biz.analytics.scheduler.AnalyticalTask;
import sirius.db.jdbc.SQLEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provides a base class for all metric computers which are invoked on a monthly basis to compute a global metric.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} as <tt>MonthlyGlobalMetricComputer</tt> so that
 * they are visible to the framework.
 * <p>
 * Note that these computers are also invoked on a daily basis for the current month to update its value
 * (if possible - as best effort scheduling is used).
 */
public abstract class SQLDailyGlobalMetricComputer extends DailyMetricComputer<SQLEntity> {

    @Override
    public Class<SQLEntity> getType() {
        return SQLEntity.class;
    }

    @Override
    public int getLevel() {
        return AnalyticalTask.DEFAULT_LEVEL + 1;
    }

    @Override
    public final void compute(LocalDate date,
                              LocalDateTime startOfPeriod,
                              LocalDateTime endOfPeriod,
                              boolean periodOutsideOfCurrentInterest,
                              SQLEntity entity) throws Exception {
        compute(date, startOfPeriod, endOfPeriod, periodOutsideOfCurrentInterest);
    }

    protected abstract void compute(LocalDate date,
                                    LocalDateTime startOfPeriod,
                                    LocalDateTime endOfPeriod,
                                    boolean periodOutsideOfCurrentInterest) throws Exception;
}
