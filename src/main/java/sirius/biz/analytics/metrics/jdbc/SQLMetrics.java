/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics.jdbc;

import sirius.biz.analytics.metrics.BasicMetrics;
import sirius.biz.analytics.metrics.Metrics;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores metrics into the appropriate tables of the underlying JDBC database.
 */
@Register(classes = Metrics.class, framework = SQLMetrics.FRAMEWORK_JDBC_METRICS)
public class SQLMetrics extends BasicMetrics {

    /**
     * Contains the name of the framework which controls whether storing metrics in a JDBC database is supported.
     */
    public static final String FRAMEWORK_JDBC_METRICS = "biz.analytics-metrics-jdbc";

    @Part
    private OMA oma;

    /**
     * Deletes the given metric if it exists.
     *
     * @param table      the table to delete from
     * @param targetType the target type to delete
     * @param targetId   the id of the target for which the metric is to be deleted
     * @param name       the name of the metric to delete
     * @param year       the year (if available) of the metric to delete
     * @param month      the month (if available) of the metric to delete
     * @param day        the day (if available) of the metric to delete
     */
    private void delete(String table,
                        String targetType,
                        String targetId,
                        String name,
                        Integer year,
                        Integer month,
                        Integer day) {
        try {
            oma.getDatabase(Mixing.DEFAULT_REALM)
               .createQuery("DELETE FROM "
                            + table
                            + " WHERE targetType = ${targetType}"
                            + " AND targetId = ${targetId}"
                            + " AND name = ${name}"
                            + " [AND year = ${year}]"
                            + " [AND month = ${month}]"
                            + " [AND day = ${day}]")
               .set("targetType", targetType)
               .set("targetId", targetId)
               .set("name", name)
               .set("year", year)
               .set("month", month)
               .set("day", day)
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(Mixing.LOG)
                      .error(e)
                      .withSystemErrorMessage("Failed to delete a metric form %s: %s (%s)", table)
                      .handle();
        }
    }

    /**
     * Updates the given metric if it exists.
     *
     * @param table      the table to update
     * @param targetType the target type to update
     * @param targetId   the id of the target for which the metric is to be update
     * @param name       the name of the metric to update
     * @param year       the year (if available) of the metric to update
     * @param month      the month (if available) of the metric to update
     * @param day        the day (if available) of the metric to update
     * @return <tt>true</tt> if the metric was updated, <tt>false</tt> otherwise
     */
    @SuppressWarnings("squid:S00107")
    @Explain("We rather have 8 parameters here and keep the logic properly encapsulated")
    private boolean update(String table,
                           String targetType,
                           String targetId,
                           String name,
                           int value,
                           Integer year,
                           Integer month,
                           Integer day) {
        try {
            int numModified = oma.getDatabase(Mixing.DEFAULT_REALM)
                                 .createQuery("UPDATE "
                                              + table
                                              + " SET value = ${value}"
                                              + " WHERE targetType = ${targetType}"
                                              + " AND targetId = ${targetId}"
                                              + " AND name = ${name}"
                                              + " [AND year = ${year}]"
                                              + " [AND month = ${month}]"
                                              + " [AND day = ${day}]")
                                 .set("value", value)
                                 .set("targetType", targetType)
                                 .set("targetId", targetId)
                                 .set("name", name)
                                 .set("year", year)
                                 .set("month", month)
                                 .set("day", day)
                                 .executeUpdate();

            return numModified > 0;
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(Mixing.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to update a metric in %s: %s (%s)", table)
                            .handle();
        }
    }

    @Override
    public void updateFact(String targetType, String targetId, String name, int value) {
        if (value == 0) {
            delete("fact", targetType, targetId, name, null, null, null);
            return;
        }

        if (update("fact", targetType, targetId, name, value, null, null, null)) {
            return;
        }

        Fact fact = new Fact();
        fact.setTargetType(targetType);
        fact.setTargetId(targetId);
        fact.setName(name);
        fact.setValue(value);
        oma.update(fact);
    }

    @Override
    public void updateYearlyMetric(String targetType, String targetId, String name, int year, int value) {
        if (value == 0) {
            delete("yearlymetric", targetType, targetId, name, year, null, null);
            return;
        }

        if (update("yearlymetric", targetType, targetId, name, value, year, null, null)) {
            return;
        }

        YearlyMetric metric = new YearlyMetric();
        metric.setTargetType(targetType);
        metric.setTargetId(targetId);
        metric.setName(name);
        metric.setYear(year);
        metric.setValue(value);
        oma.update(metric);
    }

    @Override
    public void updateMonthlyMetric(String targetType, String targetId, String name, int year, int month, int value) {
        if (value == 0) {
            delete("monthlymetric", targetType, targetId, name, year, month, null);
            return;
        }

        if (update("monthlymetric", targetType, targetId, name, value, year, month, null)) {
            return;
        }

        MonthlyMetric metric = new MonthlyMetric();
        metric.setTargetType(targetType);
        metric.setTargetId(targetId);
        metric.setName(name);
        metric.setYear(year);
        metric.setMonth(month);
        metric.setValue(value);
        oma.update(metric);
    }

    @Override
    public void updateDailyMetric(String targetType,
                                  String targetId,
                                  String name,
                                  int year,
                                  int month,
                                  int day,
                                  int value) {
        if (value == 0) {
            delete("dailymetric", targetType, targetId, name, year, month, day);
            return;
        }

        if (update("dailymetric", targetType, targetId, name, value, year, month, day)) {
            return;
        }

        DailyMetric metric = new DailyMetric();
        metric.setTargetType(targetType);
        metric.setTargetId(targetId);
        metric.setName(name);
        metric.setYear(year);
        metric.setMonth(month);
        metric.setDay(day);
        metric.setValue(value);
        oma.update(metric);
    }

    @Override
    public int queryFact(String targetType, String targetId, String name) {
        return oma.select(Fact.class)
                  .eq(Fact.TARGET_TYPE, targetType)
                  .eq(Fact.TARGET_ID, targetId)
                  .eq(Fact.NAME, name)
                  .fields(Fact.VALUE)
                  .first()
                  .map(Fact::getValue)
                  .orElse(0);
    }

    @Override
    public Map<String, Integer> queryFacts(String targetType, String targetId) {
        return oma.select(Fact.class)
                  .eq(Fact.TARGET_TYPE, targetType)
                  .eq(Fact.TARGET_ID, targetId)
                  .fields(Fact.NAME, Fact.VALUE)
                  .queryList()
                  .stream()
                  .collect(Collectors.toMap(Fact::getName, Fact::getValue));
    }

    @Override
    public List<Integer> queryYearlyMetrics(String targetType,
                                            String targetId,
                                            String name,
                                            LocalDate from,
                                            LocalDate to) {
        List<Integer> result = new ArrayList<>();
        LocalDate date = from;
        while (!date.isAfter(to)) {
            result.add(oma.select(YearlyMetric.class)
                          .eq(YearlyMetric.TARGET_TYPE, targetType)
                          .eq(YearlyMetric.TARGET_ID, targetId)
                          .eq(YearlyMetric.YEAR, date.getYear())
                          .eq(YearlyMetric.NAME, name)
                          .fields(YearlyMetric.VALUE)
                          .first()
                          .map(YearlyMetric::getValue)
                          .orElse(0));
            date = date.plusYears(1);
        }

        return result;
    }

    @Override
    public List<Integer> queryMonthlyMetrics(String targetType,
                                             String targetId,
                                             String name,
                                             LocalDate from,
                                             LocalDate to) {
        List<Integer> result = new ArrayList<>();
        LocalDate date = from;
        while (!date.isAfter(to)) {
            result.add(oma.select(MonthlyMetric.class)
                          .eq(MonthlyMetric.TARGET_TYPE, targetType)
                          .eq(MonthlyMetric.TARGET_ID, targetId)
                          .eq(MonthlyMetric.YEAR, date.getYear())
                          .eq(MonthlyMetric.MONTH, date.getMonthValue())
                          .eq(MonthlyMetric.NAME, name)
                          .fields(MonthlyMetric.VALUE)
                          .first()
                          .map(MonthlyMetric::getValue)
                          .orElse(0));
            date = date.plusMonths(1);
        }

        return result;
    }

    @Override
    public List<Integer> queryDailyMetrics(String targetType,
                                           String targetId,
                                           String name,
                                           LocalDate from,
                                           LocalDate to) {
        List<Integer> result = new ArrayList<>();
        LocalDate date = from;
        while (!date.isAfter(to)) {
            result.add(oma.select(DailyMetric.class)
                          .eq(DailyMetric.TARGET_TYPE, targetType)
                          .eq(DailyMetric.TARGET_ID, targetId)
                          .eq(DailyMetric.YEAR, date.getYear())
                          .eq(DailyMetric.MONTH, date.getMonthValue())
                          .eq(DailyMetric.DAY, date.getDayOfMonth())
                          .eq(DailyMetric.NAME, name)
                          .fields(DailyMetric.VALUE)
                          .first()
                          .map(DailyMetric::getValue)
                          .orElse(0));
            date = date.plusDays(1);
        }

        return result;
    }
}
