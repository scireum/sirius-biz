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
import sirius.kernel.health.Average;

import javax.annotation.Nullable;
import java.time.LocalDate;

/**
 * Provides a base class for monthly metric computation for each entity just like {@link MonthlyMetricComputer}.
 * <p>
 * Note however, that this is intended for compute intense tasks, therefore the batch size for this computer
 * is heavily reduced and also no daily best effort computation will take place.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} as <tt>MonthlyMetricComputer</tt> so that
 * they are visible to the framework.
 *
 * @param <E> the type of entities being processed by this computer
 */
@AutoRegister
public abstract class MonthlyLargeMetricComputer<E extends BaseEntity<?>> implements AnalyticalTask<E> {

    @Part
    @Nullable
    protected Metrics metrics;

    /**
     * Contains the maximum duration of a computation in milliseconds.
     */
    private long maxDurationMillis = 0;

    /**
     * Contains the average duration of computations in milliseconds.
     * <p>
     * This keeps track of the average duration via a sliding window.
     */
    private final Average avgDurationMillis = new Average();

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int getLevel() {
        return AnalyticalTask.DEFAULT_LEVEL;
    }

    @Override
    public void trackDuration(long durationMillis) {
        this.avgDurationMillis.addValue(durationMillis);
        this.maxDurationMillis = Math.max(this.maxDurationMillis, durationMillis);
    }

    @Override
    public long getMaxDurationMillis() {
        return maxDurationMillis;
    }

    @Override
    public Average getAvgDurationMillis() {
        return avgDurationMillis;
    }

    @Override
    public final void compute(LocalDate date, E entity, boolean bestEffort) throws Exception {
        boolean sameMonth = LocalDate.now().withDayOfMonth(1).equals(date.withDayOfMonth(1));

        // if the reference date passed to this method is in the current month, we consider the computation to be of
        // particular interest
        boolean periodOutsideOfCurrentInterest = !sameMonth;

        // usually, given the reference date, we compute the values for the respective previous month; for best-effort
        // scheduling and the current month, however, we leave the date as it is in order to obtain a preliminary value
        // for this month
        if (!bestEffort) {
            date = date.minusMonths(1);
        }

        compute(new MetricComputerContext(date,
                                          date.withDayOfMonth(1).atStartOfDay(),
                                          date.withDayOfMonth(date.lengthOfMonth())
                                              .plusDays(1)
                                              .atStartOfDay()
                                              .minusSeconds(1),
                                          periodOutsideOfCurrentInterest,
                                          bestEffort), entity);
    }

    /**
     * Performs the computation for the given parameters.
     *
     * @param context the parameters for the computation
     * @param entity  the entity to perform the computation for
     * @throws Exception in case of any problem while performing the computation
     */
    public abstract void compute(MetricComputerContext context, E entity) throws Exception;
}
