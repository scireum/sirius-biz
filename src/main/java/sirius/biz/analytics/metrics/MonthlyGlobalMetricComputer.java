/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.biz.analytics.scheduler.AnalyticalTask;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * Provides a base class for all metric computers which are invoked on a monthly basis to compute a global metric.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} as <tt>MonthlyGlobalMetricComputer</tt> so that
 * they are visible to the framework.
 * <p>
 * Note that these computers are also invoked on a daily basis for the current month to update its value
 * (if possible - as best effort scheduling is used).
 */
public abstract class MonthlyGlobalMetricComputer {

    @Part
    @Nullable
    protected Metrics metrics;

    /**
     * Performs the computation for the given date.
     *
     * @param date the date for which the computation should be performed
     * @throws Exception in case of any problem while performing the computation
     */
    public final void compute(LocalDate date) throws Exception {
        compute(date,
                date.withDayOfMonth(1).atStartOfDay(),
                date.withDayOfMonth(date.lengthOfMonth()).plusDays(1).atStartOfDay().minusSeconds(1),
                Period.between(LocalDate.now(), date).getMonths() >= 2);
    }

    /**
     * Performs the computation for the given date.
     *
     * @param date          the date for which the computation should be performed
     * @param startOfPeriod the start of the month as <tt>LocalDateTime</tt>
     * @param endOfPeriod   the end of the month as <tt>LocalDateTime</tt>
     * @param pastDate      <tt>true</tt> if the computation is performed for a past date (via the analytics command) or
     *                      <tt>false</tt> if the computation is performed for the current month.
     * @throws Exception in case of any problem while performing the computation
     */
    public abstract void compute(LocalDate date,
                                 LocalDateTime startOfPeriod,
                                 LocalDateTime endOfPeriod,
                                 boolean pastDate) throws Exception;

    /**
     * Returns the level of this computer.
     *
     * @return the priority level of this computer
     * @see AnalyticalTask#getLevel() for an in-depth description
     */
    public int getLevel() {
        return AnalyticalTask.DEFAULT_LEVEL;
    }
}
