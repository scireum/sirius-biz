/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.biz.analytics.scheduler.AnalyticalTask;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * Provides a base class for all metric computers which are invoked on a monthly basis to compute a metric for each of
 * the referenced entities.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} as <tt>MonthlyMetricComputer</tt> so that
 * they are visible to the framework.
 * <p>
 * Note that these computers are also invoked on a daily basis for the current month to update its value
 * (if possible - as best effort scheduling is used).
 *
 * @param <E> the type of entities being processed by this computer
 */
@AutoRegister
public abstract class MonthlyMetricComputer<E extends BaseEntity<?>> implements AnalyticalTask<E> {

    @Part
    @Nullable
    protected Metrics metrics;

    @Override
    public int getLevel() {
        return AnalyticalTask.DEFAULT_LEVEL;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Determines if best effort scheduling should be skipped.
     *
     * @return <tt>true</tt> to suppress the best effort scheduling, <tt>false</tt> otherwise (default)
     */
    public boolean suppressBestEffortScheduling() {
        return false;
    }

    @Override
    public final void compute(LocalDate date, E entity, boolean bestEffort) throws Exception {
        if (bestEffort && suppressBestEffortScheduling()) {
            return;
        }

        boolean sameMonth = LocalDate.now().withDayOfMonth(1).equals(date.withDayOfMonth(1));

        // if the reference date passed to this method is in the current month, we consider the computation to be of
        // particular interest – unless it is only a best-effort computation that produces preliminary values only
        boolean periodOutsideOfCurrentInterest = bestEffort || !sameMonth;

        // usually, given the reference date, we compute the values for the respective previous month; for best-effort
        // scheduling and the current month, however, we leave the date as it is in order to obtain a preliminary value
        // for this month
        if (!bestEffort || !sameMonth) {
            date = date.minusMonths(1);
        }

        compute(date,
                date.withDayOfMonth(1).atStartOfDay(),
                date.withDayOfMonth(date.lengthOfMonth()).plusDays(1).atStartOfDay().minusSeconds(1),
                periodOutsideOfCurrentInterest,
                entity);
    }

    /**
     * Performs the computation for the given date.
     *
     * @param date                           the date for which the computation should be performed
     * @param startOfPeriod                  the start of the month as <tt>LocalDateTime</tt>
     * @param endOfPeriod                    the end of the month as <tt>LocalDateTime</tt>
     * @param periodOutsideOfCurrentInterest <tt>true</tt> if the computation is performed for a past or future month (via the analytics command) or
     *                                       <tt>false</tt> if the computation is performed for the current month
     * @param entity                         the entity to perform the computation for
     * @throws Exception in case of any problem while performing the computation
     */
    public abstract void compute(LocalDate date,
                                 LocalDateTime startOfPeriod,
                                 LocalDateTime endOfPeriod,
                                 boolean periodOutsideOfCurrentInterest,
                                 E entity) throws Exception;
}
