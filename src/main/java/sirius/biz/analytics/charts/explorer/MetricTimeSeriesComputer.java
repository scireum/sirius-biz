/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.metrics.MetricQuery;
import sirius.biz.analytics.metrics.Metrics;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;

import java.util.function.Function;

/**
 * Provides a {@link TimeSeriesComputer} which loads its values from monthly {@link Metrics}.
 * <p>
 * Note that by default, a given entity is mapped so that <tt>null</tt> becomes a {@link MetricQuery#global()} query.
 * Additionally, if an entity is {@link BaseEntity} or {@link BaseEntityRef}, we automagically map to the proper
 * {@link MetricQuery#of(BaseEntity)} and {@link MetricQuery#of(BaseEntityRef)}. For all other types of entities,
 * an error will be raised as these are not supported right now.
 *
 * @param <E> the type of entities referenced by the chart
 */
public class MetricTimeSeriesComputer<E> implements TimeSeriesComputer<E> {

    @Part
    private static Metrics metrics;

    private final String monthlyMetricName;
    private String dailyMetricName;
    private Function<E, Object> projector;

    /**
     * Creates a new time-series for the given metric.
     *
     * @param monthlyMetricName the name of the metric to query
     */
    public MetricTimeSeriesComputer(String monthlyMetricName) {
        this.monthlyMetricName = monthlyMetricName;
    }

    /**
     * Specifies the metric to use if {@link Granularity#DAY} is requested.
     *
     * @param dailyMetricName the name of the daily metric to read
     * @return the computer itself for fluent method calls
     */
    public MetricTimeSeriesComputer<E> withDailyMetricName(String dailyMetricName) {
        this.dailyMetricName = dailyMetricName;
        return this;
    }

    /**
     * Provides a projector to control what gets actually put into the <tt>of</tt> of {@link MetricQuery}.
     *
     * @param projector the projector which maps the selected chart entity to the actual value to query
     * @return the computer itself for fluent method calls
     */
    public MetricTimeSeriesComputer<E> withProjector(Function<E, Object> projector) {
        this.projector = projector;
        return this;
    }

    @Override
    public void compute(E object, TimeSeries timeseries) throws Exception {
        TimeSeries effectiveTimeSeries = Strings.isFilled(dailyMetricName) ? timeseries : timeseries.toMonthlySeries();
        TimeSeriesData data = effectiveTimeSeries.createDefaultData();
        MetricQuery metricQuery = customizeQuery(project(object),
                                                 timeseries.getGranularity() == Granularity.DAY && Strings.isFilled(
                                                         dailyMetricName) ?
                                                 metrics.query().daily(dailyMetricName) :
                                                 metrics.query().monthly(monthlyMetricName));

        metricQuery.values(effectiveTimeSeries.startDates())
                   .forEach(dateAndValue -> data.addValue(dateAndValue.getFirst(), dateAndValue.getSecond()));
    }

    protected Object project(E object) {
        if (projector == null) {
            return object;
        }

        return projector.apply(object);
    }

    protected MetricQuery customizeQuery(Object object, MetricQuery metricQuery) {
        if (object == null) {
            return metricQuery.global();
        } else if (object instanceof BaseEntity<?> entity) {
            return metricQuery.of(entity);
        } else if (object instanceof BaseEntityRef<?, ?> ref) {
            return metricQuery.of(ref);
        } else {
            throw new IllegalArgumentException(object.toString());
        }
    }
}
