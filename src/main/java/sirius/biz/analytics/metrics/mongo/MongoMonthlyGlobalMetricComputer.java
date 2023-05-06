/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics.mongo;

import sirius.biz.analytics.metrics.MonthlyMetricComputer;
import sirius.biz.analytics.scheduler.AnalyticalTask;
import sirius.db.mongo.MongoEntity;

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
public abstract class MongoMonthlyGlobalMetricComputer extends MonthlyMetricComputer<MongoEntity> {

    @Override
    public Class<MongoEntity> getType() {
        return MongoEntity.class;
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
                              MongoEntity entity) throws Exception {
        compute(date, startOfPeriod, endOfPeriod, periodOutsideOfCurrentInterest);
    }

    /**
     * Performs the computation for the given date.
     *
     * @param date                           the date for which the computation should be performed
     * @param startOfPeriod                  the start of the month as <tt>LocalDateTime</tt>
     * @param endOfPeriod                    the end of the month as <tt>LocalDateTime</tt>
     * @param periodOutsideOfCurrentInterest <tt>true</tt> if the computation is performed for a past or future month (via the analytics command) or
     *                                       <tt>false</tt> if the computation is performed for the current month
     * @throws Exception in case of any problem while performing the computation
     */
    protected abstract void compute(LocalDate date,
                                    LocalDateTime startOfPeriod,
                                    LocalDateTime endOfPeriod,
                                    boolean periodOutsideOfCurrentInterest) throws Exception;
}
