/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.explorer;

import sirius.biz.analytics.events.Event;
import sirius.biz.analytics.events.EventRecorder;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.Row;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Provides a time series computer which operates on {@link Event events}.
 * <p>
 * Note that this is intended for straight forward queries which need to be adaptive
 * (like {@link sirius.biz.tycho.updates.NumberOfUpdateClicksChart} or which are re-usable like
 * {@link BrowserDistributionTimeSeriesComputer}). If a query grows and becomes too complex or isn't buildable using
 * {@link SmartQuery} anyway, a custom computer should be implemented (most probably as anonymous lambda in the
 * chart itself), which directly builds the query as readable and maintainable SQL string and collects its result.
 *
 * @param <O> the types of objects on which this computer operates
 * @param <E> the events being processed
 */
public class EventTimeSeriesComputer<O, E extends Event> implements TimeSeriesComputer<O> {

    /**
     * Provides a common aggregation which counts all events (<tt>count(*)</tt>).
     */
    public static final String AGGREGATION_EXPRESSION_COUNT = "count(*)";

    /**
     * Counts all events containing a {@link sirius.biz.analytics.events.UserData}, where the
     * {@link sirius.biz.analytics.events.UserData#USER_ID} is filled.
     */
    public static final String AGGREGATION_EXPRESSION_COUNT_LOGGED_IN = "countIf(userData_userId IS NOT NULL)";

    /**
     * Provides a proper label for {@link #AGGREGATION_EXPRESSION_COUNT_LOGGED_IN}.
     */
    public static final String LABEL_LOGGED_IN = "$EventTimeSeriesComputer.loggedIn";

    /**
     * Counts all events containing a {@link sirius.biz.analytics.events.UserData}, where the
     * {@link sirius.biz.analytics.events.UserData#USER_ID} is empty.
     */
    public static final String AGGREGATION_EXPRESSION_COUNT_ANONYMOUS = "countIf(userData_userId IS NULL)";

    /**
     * Provides a proper label for {@link #AGGREGATION_EXPRESSION_COUNT_ANONYMOUS}.
     */
    public static final String LABEL_ANONYMOUS = "$EventTimeSeriesComputer.anonymous";

    @Part
    protected static OMA oma;

    @Part
    protected static EventRecorder eventRecorder;

    private final List<Tuple<String, String>> aggregations = new ArrayList<>();
    private final BiConsumer<O, SmartQuery<E>> queryCustomizer;
    private final Class<E> eventType;

    private MetricTimeSeriesComputer<O> monthlyMetric;

    /**
     * Creates a new computer for the given event type which customizes the query based on a given object.
     *
     * @param eventType       the events to query
     * @param queryCustomizer an optional customizer which adapts the query based on the selected data object
     */
    public EventTimeSeriesComputer(Class<E> eventType, @Nullable BiConsumer<O, SmartQuery<E>> queryCustomizer) {
        this.eventType = eventType;
        this.queryCustomizer = queryCustomizer;
    }

    /**
     * Creates a new computer for the given event type without customizing the query at all.
     *
     * @param eventType the events to query
     */
    public EventTimeSeriesComputer(Class<E> eventType) {
        this(eventType, null);
    }

    /**
     * Adds an aggregation expression to collect.
     *
     * @param expression the SQL expression to add to the <tt>SELECT</tt> clause
     * @param label      the label to use or <tt>null</tt> to use the default label
     * @return the computer itself for fluent method calls
     * @see #AGGREGATION_EXPRESSION_COUNT
     */
    public EventTimeSeriesComputer<O, E> addAggregation(String expression, @Nullable String label) {
        this.aggregations.add(Tuple.create(expression, label));
        return this;
    }

    /**
     * Permits to use a {@link sirius.biz.analytics.metrics.MonthlyMetricComputer} to utilizes pre-computed monthly
     * {@linkplain sirius.biz.analytics.metrics.Metrics metrics} (if the {@link Granularity} is set to monthly as well.
     *
     * @param metricComputer the monthly metric computer to use
     * @return the computer itself for fluent method calls
     */
    public EventTimeSeriesComputer<O, E> withMonthlyMetric(MetricTimeSeriesComputer<O> metricComputer) {
        this.monthlyMetric = metricComputer;
        return this;
    }

    /**
     * Extracts the date from a result row.
     * <p>
     * Note that the columns <tt>year</tt> and <tt>month</tt> must be present. If <tt>day</tt> is present, it will
     * be used, otherwise it is assumed to be <tt>1</tt>.
     *
     * @param row the row to parse
     * @return the extracted date
     */
    public static LocalDate extractDate(Row row) {
        return LocalDate.of(row.getValue("year").asInt(0),
                            row.getValue("month").asInt(0),
                            row.tryGetValue("day").asInt(1));
    }

    @Override
    public void compute(@Nullable O object, TimeSeries timeSeries) throws Exception {
        if (monthlyMetric != null && timeSeries.getGranularity() == Granularity.MONTH) {
            monthlyMetric.compute(object, timeSeries);
            return;
        }

        SmartQuery<E> query = oma.select(eventType);

        if (queryCustomizer != null) {
            queryCustomizer.accept(object, query);
        }

        applyDateAggregations(timeSeries, query);
        applyGroupByDate(timeSeries, query);
        applyDateFilters(timeSeries, query);

        List<Tuple<String, TimeSeriesData>> timeSeriesData = new ArrayList<>();
        int counter = 1;
        for (Tuple<String, String> aggregationAndLabel : aggregations) {
            String name = "aggregation" + counter++;
            timeSeriesData.add(Tuple.create(name,
                                            Strings.isFilled(aggregationAndLabel.getSecond()) ?
                                            timeSeries.createData(aggregationAndLabel.getSecond()) :
                                            timeSeries.createDefaultData()));
            query.aggregationField(aggregationAndLabel.getFirst() + " AS " + name);
        }

        query.asSQLQuery().markAsLongRunning().iterateAll(row -> {
            LocalDate localDate = extractDate(row);
            for (Tuple<String, TimeSeriesData> nameAndData : timeSeriesData) {
                nameAndData.getSecond().addValue(localDate, row.getValue(nameAndData.getFirst()).asDouble(0d));
            }
        }, Limit.UNLIMITED);
    }

    private void applyDateAggregations(TimeSeries timeSeries, SmartQuery<E> query) {
        query.aggregationField("year(eventDate) AS year");
        query.aggregationField("month(eventDate) AS month");
        if (timeSeries.getGranularity() == Granularity.DAY) {
            query.aggregationField("day(eventDate) AS day");
        }
    }

    private void applyGroupByDate(TimeSeries timeSeries, SmartQuery<E> query) {
        if (timeSeries.getGranularity() == Granularity.DAY) {
            query.groupBy(Event.EVENT_DATE.toString());
        } else {
            query.groupBy("year(eventDate)");
            query.groupBy("month(eventDate)");
        }
    }

    private void applyDateFilters(TimeSeries timeSeries, SmartQuery<E> query) {
        query.where(OMA.FILTERS.gte(Event.EVENT_DATE, timeSeries.getStart()));
        query.where(OMA.FILTERS.lte(Event.EVENT_DATE, timeSeries.getEnd()));
    }
}
