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
 * Provides a base class for all metric computers which are invoked on a daily basis to compute a metric for each of
 * the referenced entities.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} as <tt>DailyMetricComputer</tt> so that
 * they are visible to the framework.
 *
 * @param <E> the type of entities being processed by this computer
 */
@AutoRegister
public abstract class DailyMetricComputer<E extends BaseEntity<?>> implements AnalyticalTask<E> {

    @Part
    @Nullable
    protected Metrics metrics;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int getLevel() {
        return AnalyticalTask.DEFAULT_LEVEL;
    }

    @Override
    public final void compute(LocalDate date, E entity, boolean bestEffort) throws Exception {
        boolean sameDay = LocalDate.now().equals(date);

        // if the reference date passed to this method is today, we consider the computation to be of particular
        // interest – unless it is only a best-effort computation that produces preliminary values only
        boolean periodOutsideOfCurrentInterest = !sameDay;

        // usually, given the reference date, we compute the values for the respective previous day; for best-effort
        // scheduling and the current day, however, we leave the date as it is in order to obtain a preliminary value
        // for today
        if (!bestEffort) {
            date = date.minusDays(1);
        }

        compute(date,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay().minusSeconds(1),
                periodOutsideOfCurrentInterest,
                entity);
    }

    /**
     * Performs the computation for the given date.
     *
     * @param date                           the date for which the computation should be performed
     * @param startOfPeriod                  the start of the day as <tt>LocalDateTime</tt>
     * @param endOfPeriod                    the end of the day as <tt>LocalDateTime</tt>
     * @param periodOutsideOfCurrentInterest <tt>true</tt> if the computation is performed for a past or future date (via the analytics command) or
     *                                       <tt>false</tt> if the computation is performed for this day
     * @param entity                         the entity to perform the computation for
     * @throws Exception in case of any problem while performing the computation
     */
    public abstract void compute(LocalDate date,
                                 LocalDateTime startOfPeriod,
                                 LocalDateTime endOfPeriod,
                                 boolean periodOutsideOfCurrentInterest,
                                 E entity) throws Exception;
}
