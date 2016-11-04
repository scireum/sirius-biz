/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.statistics;

import com.google.common.collect.Lists;
import sirius.db.mixing.OMA;
import sirius.db.mixing.constraints.FieldOperator;
import sirius.kernel.Lifecycle;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Counter;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricsCollector;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides a statistical warehouse which stores statistics per user defined events and objects.
 * <p>
 * An event is defined by creating a {@link StatisticalEvent}. The warehouse will then accept calls to
 * <tt>addStatistic</tt> and <tt>incrementStatistic</tt> for that event and user defined object ids. As statistics
 * can be stored for various {@link AggregationLevel}s, these are computed automatically as long as the event
 * is triggered for the lowest (finest) level.
 * <p>
 * Additionally various queries are provided to simplify the extraction and visualization of the recorded statistics.
 */
@Framework("statistics")
@Register(classes = {Statistics.class, BackgroundLoop.class, Lifecycle.class, MetricProvider.class})
public class Statistics extends BackgroundLoop implements Lifecycle, MetricProvider {

    @Part
    private OMA oma;

    public static final Log LOG = Log.get("statistics");

    private Counter statisticUpdates = new Counter();

    private static class RecordedEvent {
        StatisticalEvent event;
        String objectId;
        int value;
        boolean delete;
    }

    private List<RecordedEvent> todoQueue = Lists.newArrayList();

    /**
     * Increments the statistic for the given event of the given object by the given value.
     *
     * @param event    the event to increment the statistic for
     * @param objectId the object to increment the statistic for
     * @param value    the value by which the statistic should be incremented
     */
    public void addStatistic(StatisticalEvent event, String objectId, int value) {
        RecordedEvent recordedEvent = new RecordedEvent();
        recordedEvent.event = event;
        recordedEvent.objectId = objectId;
        recordedEvent.value = value;
        synchronized (this) {
            todoQueue.add(recordedEvent);
        }
    }

    /**
     * Boilerplate method to invoke <tt>addStatistic</tt> with <b>1</b> as value.
     *
     * @param event    the event to increment the statistic for
     * @param objectId the object to increment the statistic for
     */
    public void incrementStatistic(StatisticalEvent event, String objectId) {
        addStatistic(event, objectId, 1);
    }

    /**
     * Deletes all recorded statistics for the given object.
     *
     * @param objectId the id identifying the object for which the statistics should be deleted
     */
    public void deleteStatistic(String objectId) {
        RecordedEvent recordedEvent = new RecordedEvent();
        recordedEvent.objectId = objectId;
        recordedEvent.delete = true;
        synchronized (this) {
            todoQueue.add(recordedEvent);
        }
    }

    protected void commitStatistics() {
        Watch w = Watch.start();
        final List<RecordedEvent> copy;
        synchronized (this) {
            copy = Lists.newArrayList(todoQueue);
            todoQueue.clear();
        }

        for (RecordedEvent event : copy) {
            try {
                statisticUpdates.inc();
                if (event.delete) {
                    handleDelete(event);
                } else {
                    handleIncrement(event);
                }
            } catch (SQLException e) {
                Exceptions.handle(LOG, e);
            }
        }
    }

    private void handleIncrement(RecordedEvent event) throws SQLException {
        LocalDate now = LocalDate.now();
        for (AggregationLevel level : AggregationLevel.values()) {
            if (event.event.getFinestAggregationLevel().ordinal() <= level.ordinal()) {
                processIncrement(event.event.getEventName(),
                                 level,
                                 event.objectId,
                                 AggregationLevel.convertDate(now, level),
                                 event.value);
            }
        }
    }

    private void handleDelete(RecordedEvent event) throws SQLException {
        oma.getDatabase()
           .createQuery("DELETE FROM statisticvalue WHERE objectId = ${id}")
           .set("id", event.objectId)
           .executeUpdate();
    }

    private void processIncrement(String event, AggregationLevel level, String objectId, LocalDate tod, int value)
            throws SQLException {
        String query = "UPDATE statisticvalue SET statisticValue = statisticValue + ${value} "
                       + "WHERE objectId = ${objectId} "
                       + "AND event = ${event} "
                       + "AND level = ${level} "
                       + "AND tod = ${tod} ";
        int rowsUpdated = oma.getDatabase()
                             .createQuery(query)
                             .set("objectId", objectId)
                             .set("event", event)
                             .set("level", level.name())
                             .set("value", value)
                             .set("tod", java.sql.Date.valueOf(tod))
                             .executeUpdate();
        if (rowsUpdated == 0) {
            oma.getDatabase()
               .insertRow("statisticvalue",
                          Context.create()
                                 .set("objectId", objectId)
                                 .set("event", event)
                                 .set("level", level.name())
                                 .set("statisticValue", value)
                                 .set("tod", java.sql.Date.valueOf(tod)));
        }
    }

    /**
     * Returns a list of all recorded statistics for the given period, event and object.
     *
     * @param event    the event for which the statistic should be queried
     * @param level    the aggregation level of the recorded values
     * @param objectId the object for which the statistic was recorded
     * @param dateFrom the start of the search period
     * @param dateTo   the end of the search period
     * @return a list of all statistical values recorded for the given parameters
     */
    public List<Long> getStatistics(StatisticalEvent event,
                                    AggregationLevel level,
                                    String objectId,
                                    LocalDate dateFrom,
                                    LocalDate dateTo) {

        return oma.select(StatisticValue.class)
                  .eq(StatisticValue.EVENT, event.getEventName())
                  .eq(StatisticValue.LEVEL, level)
                  .eq(StatisticValue.OBJECT_ID, objectId)
                  .where(FieldOperator.on(StatisticValue.TOD).greaterOrEqual(dateFrom))
                  .where(FieldOperator.on(StatisticValue.TOD).lessOrEqual(dateTo))
                  .queryList()
                  .stream()
                  .map(StatisticValue::getStatisticValue)
                  .collect(Collectors.toList());
    }

    /**
     * Determines the recorded statistic value for the given parameters.
     *
     * @param event    the event of the statistic value to find
     * @param level    the aggregation level of the statistic value to find
     * @param objectId the object of the statistic value to find
     * @param date     the date of the statistic value to find
     * @return the statistic value computed or recorded for the given parameters or 0 if no value was found
     */
    public long getStatisticValue(StatisticalEvent event, AggregationLevel level, String objectId, LocalDate date) {
        StatisticValue e = findStatisticValue(event, level, objectId, date);
        if (e == null) {
            return 0;
        }
        return e.getStatisticValue();
    }

    protected StatisticValue findStatisticValue(StatisticalEvent event,
                                                AggregationLevel level,
                                                String objectId,
                                                LocalDate date) {
        return oma.select(StatisticValue.class)
                  .eq(StatisticValue.EVENT, event.getEventName())
                  .eq(StatisticValue.LEVEL, level)
                  .eq(StatisticValue.OBJECT_ID, objectId)
                  .eq(StatisticValue.TOD, AggregationLevel.convertDate(date, level))
                  .queryFirst();
    }

    /**
     * Returns a list of statistic values for the last 30 days.
     *
     * @param event    the event of the statistic to fetch
     * @param objectId the object of the statistic to fetch
     * @return a list of the values of the last 30 days starting from the current day
     */
    public List<Long> getLastThirtyDays(StatisticalEvent event, String objectId) {
        LocalDate date = LocalDate.now();
        List<Long> result = Lists.newArrayListWithCapacity(30);
        boolean dataFound = false;
        for (int day = 0; day < 30; day++) {
            StatisticValue e = findStatisticValue(event, AggregationLevel.DAYS, objectId, date);
            if (e != null) {
                dataFound = true;
                result.add(e.getStatisticValue());
            } else {
                result.add(0L);
            }
            date = date.minusDays(1);
        }

        if (dataFound) {
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Computes a statistical report for the current month.
     *
     * @param event    the event of the statistic to fetch
     * @param objectId the object of the statistic to fetch
     * @return the statistical values recorded for the current month
     */
    public MonthStatistic getThisMonth(StatisticalEvent event, String objectId) {
        return new MonthStatistic(getStatisticValue(event, AggregationLevel.MONTHS, objectId, LocalDate.now()),
                                  getStatisticValue(event,
                                                    AggregationLevel.MONTHS,
                                                    objectId,
                                                    LocalDate.now().minusMonths(1)),
                                  getStatisticValue(event,
                                                    AggregationLevel.MONTHS,
                                                    objectId,
                                                    LocalDate.now().minusYears(1)),
                                  LocalDate.now());
    }

    /**
     * Computes a statistical report for the previous month.
     *
     * @param event    the event of the statistic to fetch
     * @param objectId the object of the statistic to fetch
     * @return the statistical values recorded for the previous month
     */
    public MonthStatistic getLastMonth(StatisticalEvent event, String objectId) {
        return new MonthStatistic(getStatisticValue(event,
                                                    AggregationLevel.MONTHS,
                                                    objectId,
                                                    LocalDate.now().minusMonths(1)),
                                  getStatisticValue(event,
                                                    AggregationLevel.MONTHS,
                                                    objectId,
                                                    LocalDate.now().minusMonths(2)),
                                  getStatisticValue(event,
                                                    AggregationLevel.MONTHS,
                                                    objectId,
                                                    LocalDate.now().minusMonths(1).minusYears(1)),
                                  LocalDate.now().minusMonths(1));
    }

    /**
     * Returns a list of statistic values for the last 12 months.
     *
     * @param event    the event of the statistic to fetch
     * @param objectId the object of the statistic to fetch
     * @return a list of the values of the last 12 month starting from the current month
     */
    public List<Long> getLast12Month(StatisticalEvent event, String objectId) {
        List<Long> result = Lists.newArrayListWithCapacity(12);
        boolean dataFound = false;
        LocalDate date = LocalDate.now();
        for (int month = 0; month < 12; month++) {
            StatisticValue e = findStatisticValue(event, AggregationLevel.MONTHS, objectId, date);
            if (e != null) {
                dataFound = true;
                result.add(e.getStatisticValue());
            } else {
                result.add(0L);
            }
            date = date.minusMonths(1);
        }
        if (dataFound) {
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Computes a statistical report for the current year
     *
     * @param event    the event of the statistic to fetch
     * @param objectId the object of the statistic to fetch
     * @return the statistical values recorded for the current year
     */
    public YearStatistic getYearStatistic(StatisticalEvent event, String objectId) {
        return new YearStatistic(getStatisticValue(event, AggregationLevel.YEARS, objectId, LocalDate.now()),
                                 getStatisticValue(event,
                                                   AggregationLevel.YEARS,
                                                   objectId,
                                                   LocalDate.now().minusYears(1)));
    }

    @Override
    public void started() {
        // Nothing to do here
    }

    @Override
    public void stopped() {
        // Ensure, that statistics are completely committed when shutting down the system
        commitStatistics();
    }

    @Override
    public void awaitTermination() {

    }

    @Nonnull
    @Override
    public String getName() {
        return "Statistics";
    }

    @Override
    protected void doWork() throws Exception {
        commitStatistics();
    }

    @Override
    public void gather(MetricsCollector collector) {
        collector.differentialMetric("statistics-updates",
                                     "statistics-updates",
                                     "Statistics Updated",
                                     statisticUpdates.getCount(),
                                     "1/min");
    }
}
