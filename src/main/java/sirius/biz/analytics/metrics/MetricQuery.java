/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.db.mixing.BaseEntity;

import javax.annotation.CheckReturnValue;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a fluent API to create and execute queries against the metrics database.
 */
public class MetricQuery {

    /**
     * Specifies the maximal number of values returned for daily metrics.
     */
    public static final int MAX_DAILY_METRICS = 400;

    /**
     * Specifies the maximal number of values returned for monthly metrics.
     */
    public static final int MAX_MONTHLY_METRICS = 100;

    /**
     * Specifies the maximal number of values returned for yearly metrics.
     */
    public static final int MAX_YEARLY_METRICS = 100;

    protected enum Interval {
        YEARLY, MONTHLY, DAILY, FACT
    }

    private final BasicMetrics<?> metrics;
    private String metricName;
    private String targetType;
    private String targetId;
    private Interval interval;

    protected MetricQuery(BasicMetrics<?> metrics) {
        this.metrics = metrics;
    }

    /**
     * Queries the yearly metric with the given name.
     *
     * @param name the metric to query
     * @return the query itself for fluent method calls
     */
    @CheckReturnValue
    public MetricQuery yearly(String name) {
        this.metricName = name;
        this.interval = Interval.YEARLY;

        return this;
    }

    /**
     * Queries the monthly metric with the given name.
     *
     * @param name the metric to query
     * @return the query itself for fluent method calls
     */
    @CheckReturnValue
    public MetricQuery monthly(String name) {
        this.metricName = name;
        this.interval = Interval.MONTHLY;

        return this;
    }

    /**
     * Queries the daily metric with the given name.
     *
     * @param name the metric to query
     * @return the query itself for fluent method calls
     */
    @CheckReturnValue
    public MetricQuery daily(String name) {
        this.metricName = name;
        this.interval = Interval.DAILY;

        return this;
    }

    /**
     * Queries the fact with the given name.
     *
     * @param name the metric to query
     * @return the query itself for fluent method calls
     */
    @CheckReturnValue
    public MetricQuery fact(String name) {
        this.metricName = name;
        this.interval = Interval.FACT;

        return this;
    }

    /**
     * Speficies the object to query metrics for.
     *
     * @param targetType the type of the entity to query metrics for
     * @param targetId   the id of the entity to query metrics for
     * @return the query itself for fluent method calls
     */
    @CheckReturnValue
    public MetricQuery of(String targetType, String targetId) {
        this.targetType = targetType;
        this.targetId = targetId;

        return this;
    }

    /**
     * Specifies the entity to query metrics for.
     *
     * @param entity the entity to query metrics for
     * @return the query itself for fluent method calls
     */
    @CheckReturnValue
    public MetricQuery of(BaseEntity<?> entity) {
        return of(entity.getTypeName(), entity.getIdAsString());
    }

    /**
     * Specifies that global metrics (not associated to an entity but rather system wide values) should be queried.
     *
     * @return the query itself for fluent method calls
     */
    @CheckReturnValue
    public MetricQuery global() {
        return of(BasicMetrics.GLOBAL, BasicMetrics.GLOBAL);
    }

    /**
     * Fetches all metrics starting from the <tt>startDate</tt> up until the <tt>untilDate</tt> is reached.
     *
     * @param startDate the first date to fetch metrics for
     * @param untilDate the last date to fetch metrics for
     * @return the list of metrics fetched for the given period. Note that there are internal circuit breakers in case
     * too many metrics would be requested. In this case a limited list is returned.
     */
    public List<Integer> values(LocalDate startDate, LocalDate untilDate) {
        assertParametersArePresent();
        List<Integer> result = new ArrayList<>();
        LocalDate date = startDate;
        LocalDate endDate = untilDate == null ? LocalDate.now() : untilDate;
        AtomicInteger limit = new AtomicInteger(determineLimit(interval));
        while (!date.isAfter(endDate) && limit.decrementAndGet() > 0) {
            result.add(metrics.executeQuery(interval,
                                            targetType,
                                            targetId,
                                            metricName,
                                            date.getYear(),
                                            date.getMonthValue(),
                                            date.getDayOfMonth()).orElse(0));
            date = increment(date, interval);
        }

        return result;
    }

    private void assertParametersArePresent() {
        if (interval == null) {
            throw new IllegalStateException("No interval has been chosen for the metric query: " + this);
        }
        if (targetType == null) {
            throw new IllegalStateException("No targetType has been chosen for the metric query: " + this);
        }
        if (targetId == null) {
            throw new IllegalStateException("No targetId has been chosen for the metric query: " + this);
        }
    }

    /**
     * Fetches the requested number of metrics starting from the <tt>startDate</tt>.
     *
     * @param startDate      the first date to fetch metrics for
     * @param numberOfValues the number of metrics to fetch
     * @return the list of metrics fetched for the given period. Note that there are internal circuit breakers in case
     * too many metrics would be requested. In this case a limited list is returned.
     */
    public List<Integer> valuesFrom(LocalDate startDate, int numberOfValues) {
        assertParametersArePresent();
        List<Integer> result = new ArrayList<>();
        LocalDate date = startDate;
        AtomicInteger limit = new AtomicInteger(Math.min(numberOfValues, determineLimit(interval)));
        while (limit.decrementAndGet() > 0) {
            result.add(metrics.executeQuery(interval,
                                            targetType,
                                            targetId,
                                            metricName,
                                            date.getYear(),
                                            date.getMonthValue(),
                                            date.getDayOfMonth()).orElse(0));
            date = increment(date, interval);
        }

        return result;
    }

    /**
     * Fetches the requested number of metrics up until the <tt>endDate</tt>.
     *
     * @param endDate        the last date to fetch metrics for
     * @param numberOfValues the number of metrics to fetch (before the given end date)
     * @return the list of metrics fetched for the given period (sorted by date ascending).
     * Note that there are internal circuit breakers in case too many metrics would be requested.
     * In this case a limited list is returned.
     */
    public List<Integer> valuesUntil(LocalDate endDate, int numberOfValues) {
        assertParametersArePresent();
        List<Integer> result = new ArrayList<>();
        LocalDate date = endDate;
        AtomicInteger limit = new AtomicInteger(Math.min(numberOfValues, determineLimit(interval)));
        while (limit.decrementAndGet() > 0) {
            result.add(0,
                       metrics.executeQuery(interval,
                                            targetType,
                                            targetId,
                                            metricName,
                                            date.getYear(),
                                            date.getMonthValue(),
                                            date.getDayOfMonth()).orElse(0));
            date = decrement(date, interval);
        }

        return result;
    }

    private LocalDate increment(LocalDate date, Interval interval) {
        switch (interval) {
            case DAILY:
                return date.plusDays(1);
            case MONTHLY:
                return date.plusMonths(1);
            case YEARLY:
                return date.plusYears(1);
            default:
                // In case of a fact, there is no point in changing the date. However, to be 100% sure that
                // any loop etc will terminate we output the extreme here...
                return LocalDate.MAX;
        }
    }

    private LocalDate decrement(LocalDate date, Interval interval) {
        switch (interval) {
            case DAILY:
                return date.minusDays(1);
            case MONTHLY:
                return date.minusMonths(1);
            case YEARLY:
                return date.minusYears(1);
            default:
                // In case of a fact, there is no point in changing the date. However, to be 100% sure that
                // any loop etc will terminate we output the extreme here...
                return LocalDate.MIN;
        }
    }

    private int determineLimit(Interval interval) {
        switch (interval) {
            case DAILY:
                return MAX_DAILY_METRICS;
            case MONTHLY:
                return MAX_MONTHLY_METRICS;
            case YEARLY:
                return MAX_YEARLY_METRICS;
            case FACT:
                return 1;
            default:
                throw new IllegalStateException("No interval is specified!");
        }
    }

    /**
     * Fetches the most current value available for the given metric.
     * <p>
     * This will attempt to resolve the metric using the current date. If no value is present, {@link #lastValue()}
     * is used.
     *
     * @return the must current value for the queried metric
     */
    public int currentValue() {
        assertParametersArePresent();
        Optional<Integer> metric = metrics.executeQuery(interval,
                                                        targetType,
                                                        targetId,
                                                        metricName,
                                                        LocalDate.now().getYear(),
                                                        LocalDate.now().getMonthValue(),
                                                        LocalDate.now().getDayOfMonth());
        if (metric.isPresent() || interval == Interval.FACT) {
            return metric.orElse(0);
        }

        return lastValue();
    }

    /**
     * Fetches the "last" metric value which should be available.
     * <p>
     * <ul>
     *     <li>For <b>daily metrics</b>, this is the value for <b>yesterday</b>.</li>
     *     <li>For <b>monthly metrics</b>, this is the value for <b>the previous month</b>.</li>
     *     <li>For <b>yearly metrics</b>, this is the value for <b>the previous year</b>.</li>
     *     <li>For <b>facts</b>, this is the value itself.</li>
     * </ul>
     *
     * @return the value for the metric which should currently be safely available
     */
    public int lastValue() {
        assertParametersArePresent();
        LocalDate date = decrement(LocalDate.now(), interval);

        return metrics.executeQuery(interval,
                                    targetType,
                                    targetId,
                                    metricName,
                                    date.getYear(),
                                    date.getMonthValue(),
                                    date.getDayOfMonth()).orElse(0);
    }

    @Override
    public String toString() {
        return "MetricQuery{"
               + "metricName='"
               + metricName
               + '\''
               + ", targetType='"
               + targetType
               + '\''
               + ", targetId='"
               + targetId
               + '\''
               + ", interval="
               + interval
               + '}';
    }
}
