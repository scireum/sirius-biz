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
import sirius.kernel.di.std.Part;

import java.util.function.Function;

/**
 * Provides a {@link TimeseriesComputer} which loads its values from monthly {@link Metrics}.
 * <p>
 * Note that by default, a given entity is mapped so that <tt>null</tt> becomes a {@link MetricQuery#global()} query.
 * Additionally, if an entity is {@link BaseEntity} or {@link BaseEntityRef}, we automagically map to the proper
 * {@link MetricQuery#of(BaseEntity)} and {@link MetricQuery#of(BaseEntityRef)}. For all other types of entities,
 * an error will be raised as these are not supported right now.
 *
 * @param <E> the type of entities referenced by the chart
 */
public class MonthlyMetricTimeseries<E> implements TimeseriesComputer<E> {

    @Part
    private static Metrics metrics;

    private final String metricName;
    private Function<E, Object> projector;

    /**
     * Creates a new timeseries for the given metric.
     *
     * @param metricName the name of the metric to query
     */
    public MonthlyMetricTimeseries(String metricName) {
        this.metricName = metricName;
    }

    /**
     * Provides a projector to control what gets actually put into the <tt>of</tt> of {@link MetricQuery}.
     *
     * @param projector the projector which maps the selected chart entity to the actual value to query
     * @return the computer itself for fluent method calls
     */
    public MonthlyMetricTimeseries<E> withProjector(Function<E, Object> projector) {
        this.projector = projector;
        return this;
    }

    @Override
    public void compute(E object, Timeseries timeseries) throws Exception {
        Timeseries effectiveTimeseries = timeseries.toMonthlySeries();
        TimeseriesData data = effectiveTimeseries.createDefaultData();
        MetricQuery metricQuery = customizeQuery(project(object), metrics.query().monthly(metricName));

        metricQuery.values(effectiveTimeseries.startDates())
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
