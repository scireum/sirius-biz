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
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mixing;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Stores metrics into the appropriate tables of the underlying JDBC database.
 */
@Register(classes = Metrics.class, framework = SQLMetrics.FRAMEWORK_JDBC_METRICS)
public class SQLMetrics extends BasicMetrics<SQLEntity> {

    /**
     * Contains the name of the framework which controls whether storing metrics in a JDBC database is supported.
     */
    public static final String FRAMEWORK_JDBC_METRICS = "biz.analytics-metrics-jdbc";

    @Part
    private OMA oma;

    @Part
    private Mixing mixing;

    @Override
    protected Class<? extends SQLEntity> getFactType() {
        return Fact.class;
    }

    @Override
    protected Class<? extends SQLEntity> getYearlyMetricType() {
        return YearlyMetric.class;
    }

    @Override
    protected Class<? extends SQLEntity> getMonthlyMetricType() {
        return MonthlyMetric.class;
    }

    @Override
    protected Class<? extends SQLEntity> getDailyMetricType() {
        return DailyMetric.class;
    }

    @Override
    protected void deleteMetric(Class<? extends SQLEntity> table,
                                String targetType,
                                String targetId,
                                String name,
                                Integer year,
                                Integer month,
                                Integer day) {
        try {
            oma.deleteStatement(table)
               .where(Fact.TARGET_TYPE, targetType)
               .where(Fact.TARGET_ID, targetId)
               .where(Fact.NAME, name)
               .whereIgnoreNull(YearlyMetric.YEAR, year)
               .whereIgnoreNull(MonthlyMetric.MONTH, month)
               .whereIgnoreNull(DailyMetric.DAY, day)
               .executeUpdate();
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(Mixing.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete a metric in %s: %s (%s)", table)
                            .handle();
        }
    }

    @Override
    protected boolean updateMetric(Class<? extends SQLEntity> table,
                                   String targetType,
                                   String targetId,
                                   String name,
                                   int value,
                                   Integer year,
                                   Integer month,
                                   Integer day) {
        try {
            int numModified = oma.updateStatement(table)
                                 .set(Fact.VALUE, value)
                                 .where(Fact.TARGET_TYPE, targetType)
                                 .where(Fact.TARGET_ID, targetId)
                                 .where(Fact.NAME, name)
                                 .whereIgnoreNull(YearlyMetric.YEAR, year)
                                 .whereIgnoreNull(MonthlyMetric.MONTH, month)
                                 .whereIgnoreNull(DailyMetric.DAY, day)
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
    protected void createFact(String targetType, String targetId, String name, int value) {
        Fact fact = new Fact();
        fact.setTargetType(targetType);
        fact.setTargetId(targetId);
        fact.setName(name);
        fact.setValue(value);
        oma.update(fact);
    }

    @Override
    protected void createYearlyMetric(String targetType, String targetId, String name, int value, int year) {
        YearlyMetric metric = new YearlyMetric();
        metric.setTargetType(targetType);
        metric.setTargetId(targetId);
        metric.setName(name);
        metric.setYear(year);
        metric.setValue(value);
        oma.update(metric);
    }

    @Override
    protected void createMonthlyMetric(String targetType,
                                       String targetId,
                                       String name,
                                       int year,
                                       int month,
                                       int value) {
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
    protected void createDailyMetric(String targetType,
                                     String targetId,
                                     String name,
                                     int year,
                                     int month,
                                     int day,
                                     int value) {
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
    protected Optional<Integer> queryMetric(Class<? extends SQLEntity> table,
                                            String targetType,
                                            String targetId,
                                            String name,
                                            Integer year,
                                            Integer month,
                                            Integer day) {
        try {
            return oma.select(table)
                      .fields(Fact.VALUE)
                      .eq(Fact.TARGET_TYPE, targetType)
                      .eq(Fact.TARGET_ID, targetId)
                      .eq(Fact.NAME, name)
                      .eqIgnoreNull(YearlyMetric.YEAR, year)
                      .eqIgnoreNull(MonthlyMetric.MONTH, month)
                      .eqIgnoreNull(DailyMetric.DAY, day)
                      .asSQLQuery()
                      .first()
                      .map(row -> row.getValue(Fact.VALUE).asInt(0));
        } catch (SQLException ex) {
            throw Exceptions.handle(OMA.LOG, ex);
        }
    }
}
