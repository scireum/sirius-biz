/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.events.Event;
import sirius.biz.analytics.events.EventRecorder;
import sirius.db.jdbc.SQLQuery;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;

import java.time.LocalDate;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Computes a timeseries by creating an SQL query against an {@link Event} table.
 * <p>
 * Note that many of the methods provided here directly add strings into the generated SQL query. Therefore, these
 * strings should be constant and known to be safe, as any user input might lead to SQL injection bugs! Values can
 * be safely injected via {@link #withWhere(String, BiConsumer)}.
 * <p>
 * A minimal working example can be found in {@link EventCountingTimeSeries}.
 *
 * @param <O> the type of entities being referenced
 */
public class EventTimeSeries<O> implements TimeSeriesComputer<O> {

    @Part
    protected static EventRecorder eventRecorder;

    @Part
    protected static Mixing mixing;

    private final Class<? extends Event> eventType;
    private String fieldsToSelect;
    private Function<TimeSeries, BiConsumer<LocalDate, Function<String, Double>>> rowProcessorFactory;
    private String condition;
    private BiConsumer<O, SQLQuery> filterCustomizer;
    private String groupBy;

    /**
     * Creates a new timeseries for the given event type.
     *
     * @param eventType the type of events to query
     */
    public EventTimeSeries(Class<? extends Event> eventType) {
        this.eventType = eventType;
    }

    /**
     * Specifies which fields to query (to put in the SELECT clause) and how to process them.
     *
     * @param fieldsToSelect      one or more field to query (this will be put behind the SELECT in the generated query).
     * @param rowProcessorFactory creates a row-processor, which is then supplied with all rows of the resulting query.
     *                            This can create (and then fill) one or more {@link TimeSeriesData} instances based
     *                            on the query results.
     * @return the timeseries computer itself for fluent method calls
     */
    public EventTimeSeries<O> withSelect(String fieldsToSelect,
                                         Function<TimeSeries, BiConsumer<LocalDate, Function<String, Double>>> rowProcessorFactory) {
        this.fieldsToSelect = fieldsToSelect;
        this.rowProcessorFactory = rowProcessorFactory;

        return this;
    }

    /**
     * Provides a safe way of adding a parameterized WHERE clause.
     *
     * @param condition        the SQL to add. This must be a constant string. Variables can be referenced like <tt>${var}</tt>
     *                         which are then provided by the <tt>filterCustomizer</tt>
     * @param filterCustomizer uses {@link SQLQuery#set(String, Object)} to provide the variables used by
     *                         <tt>condition</tt>
     * @return the timeseries computer itself for fluent method calls
     */
    public EventTimeSeries<O> withWhere(String condition, BiConsumer<O, SQLQuery> filterCustomizer) {
        this.condition = condition;
        this.filterCustomizer = filterCustomizer;

        return this;
    }

    /**
     * Permits to add a constant and parameter-free WHERE clause.
     *
     * @param condition the condition to add to the SQL
     * @return the timeseries computer itself for fluent method calls
     */
    public EventTimeSeries<O> withWhere(String condition) {
        this.condition = condition;

        return this;
    }

    /**
     * Permits to add one or more additional GROUP BY clauses.
     *
     * @param groupBy the clauses to add
     * @return the timeseries computer itself for fluent method calls
     */
    public EventTimeSeries<O> withGroupBy(String groupBy) {
        this.groupBy = groupBy;

        return this;
    }

    @Override
    public void compute(O object, TimeSeries timeseries) throws Exception {
        if (fieldsToSelect == null) {
            throw new IllegalStateException("Missing call to withSelect");
        }
        String query = "SELECT " + fieldsToSelect + ", " + "MONTH(eventDate) as month, " + "YEAR(eventDate) as year";
        if (timeseries.getGranularity() == Granularity.DAY) {
            query += ", DAY(eventDate) as day";
        } else {
            query += ", 1 as day";
        }

        query += " FROM "
                 + mixing.getDescriptor(eventType).getRelationName()
                 + " WHERE eventDate >= ${start}"
                 + "   AND eventDate <= ${end} ";

        if (Strings.isFilled(condition)) {
            query += "AND " + condition;
        }

        query += " GROUP BY ";
        if (timeseries.getGranularity() == Granularity.DAY) {
            query += "eventDate";
        } else {
            query += "MONTH(eventDate), YEAR(eventDate)";
        }

        if (Strings.isFilled(groupBy)) {
            query += "," + groupBy;
        }

        SQLQuery sqlQuery = eventRecorder.createQuery(query);
        sqlQuery.set("start", timeseries.getStart()).set("end", timeseries.getEnd());

        if (filterCustomizer != null) {
            filterCustomizer.accept(object, sqlQuery);
        }

        BiConsumer<LocalDate, Function<String, Double>> rowProcessor = rowProcessorFactory.apply(timeseries);
        sqlQuery.iterateAll(row -> {
            LocalDate date = LocalDate.of(row.getValue("year").asInt(0),
                                          row.getValue("month").asInt(1),
                                          row.getValue("day").asInt(1));
            rowProcessor.accept(date, field -> row.getValue(field).asDouble(0));
        }, Limit.UNLIMITED);
    }
}
