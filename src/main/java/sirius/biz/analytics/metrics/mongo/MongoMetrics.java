/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics.mongo;

import sirius.biz.analytics.metrics.BasicMetrics;
import sirius.biz.analytics.metrics.Metrics;
import sirius.db.mongo.Deleter;
import sirius.db.mongo.Inserter;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.Updater;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores metrics into the appropriate collections of the underlying MongoDB.
 */
@Register(classes = Metrics.class, framework = MongoMetrics.FRAMEWORK_MONGO_METRICS)
public class MongoMetrics extends BasicMetrics<MongoEntity> {

    /**
     * Contains the name of the framework which controls whether storing metrics in MongoDB is supported.
     */
    public static final String FRAMEWORK_MONGO_METRICS = "biz.analytics-metrics-mongo";

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

    @Override
    protected Class<? extends MongoEntity> getFactType() {
        return Fact.class;
    }

    @Override
    protected Class<? extends MongoEntity> getYearlyMetricType() {
        return YearlyMetric.class;
    }

    @Override
    protected Class<? extends MongoEntity> getMonthlyMetricType() {
        return MonthlyMetric.class;
    }

    @Override
    protected Class<? extends MongoEntity> getDailyMetricType() {
        return DailyMetric.class;
    }

    @Override
    protected void deleteMetric(Class<? extends MongoEntity> type,
                                String targetType,
                                String targetId,
                                String name,
                                Integer year,
                                Integer month,
                                Integer day) {
        Deleter deleteQuery = mongo.delete()
                                   .where(Fact.TARGET_TYPE, targetType)
                                   .where(Fact.TARGET_ID, targetId)
                                   .where(Fact.NAME, name)
                                   .whereIgnoreNull(YearlyMetric.YEAR, year)
                                   .whereIgnoreNull(MonthlyMetric.MONTH, month)
                                   .whereIgnoreNull(DailyMetric.DAY, day);

        deleteQuery.singleFrom(type);
    }

    @Override
    protected boolean updateMetric(Class<? extends MongoEntity> type,
                                   String targetType,
                                   String targetId,
                                   String name,
                                   int value,
                                   Integer year,
                                   Integer month,
                                   Integer day) {
        Updater updateQuery = mongo.update()
                                   .where(Fact.TARGET_TYPE, targetType)
                                   .where(Fact.TARGET_ID, targetId)
                                   .where(Fact.NAME, name)
                                   .whereIgnoreNull(YearlyMetric.YEAR, year)
                                   .whereIgnoreNull(MonthlyMetric.MONTH, month)
                                   .whereIgnoreNull(DailyMetric.DAY, day);

        return updateQuery.set(Fact.VALUE, value).executeFor(type).getMatchedCount() > 0;
    }

    /**
     * Inserts the given metric assuming that it doesn't exist yet.
     *
     * @param type       the type of metric to insert
     * @param targetType the target type to insert
     * @param targetId   the id of the target for which the metric is to be insert
     * @param name       the name of the metric to insert
     * @param year       the year (if available) of the metric to insert
     * @param month      the month (if available) of the metric to insert
     * @param day        the day (if available) of the metric to insert
     */
    @SuppressWarnings("squid:S00107")
    @Explain("We rather have 8 parameters here and keep the logic properly encapsulated")
    private void insert(Class<? extends MongoEntity> type,
                        String targetType,
                        String targetId,
                        String name,
                        int value,
                        Integer year,
                        Integer month,
                        Integer day) {

        Inserter insertQuery = mongo.insert()
                                    .set(Fact.TARGET_TYPE, targetType)
                                    .set(Fact.TARGET_ID, targetId)
                                    .set(Fact.NAME, name)
                                    .set(Fact.VALUE, value);

        if (year != null) {
            insertQuery.set(YearlyMetric.YEAR, year);
        }
        if (month != null) {
            insertQuery.set(MonthlyMetric.MONTH, month);
        }
        if (day != null) {
            insertQuery.set(DailyMetric.DAY, day);
        }

        insertQuery.set(Fact.VALUE, value).into(type);
    }

    @Override
    protected void createFact(String targetType, String targetId, String name, int value) {
        mongo.insert()
             .set(Fact.TARGET_TYPE, targetType)
             .set(Fact.TARGET_ID, targetId)
             .set(Fact.NAME, name)
             .set(Fact.VALUE, value)
             .into(Fact.class);
    }

    @Override
    protected void createYearlyMetric(String targetType, String targetId, String name, int value, int year) {
        mongo.insert()
             .set(YearlyMetric.TARGET_TYPE, targetType)
             .set(YearlyMetric.TARGET_ID, targetId)
             .set(YearlyMetric.NAME, name)
             .set(YearlyMetric.VALUE, value)
             .set(YearlyMetric.YEAR, year)
             .into(Fact.class);
    }

    @Override
    protected void createMonthlyMetric(String targetType,
                                       String targetId,
                                       String name,
                                       int year,
                                       int month,
                                       int value) {
        mongo.insert()
             .set(MonthlyMetric.TARGET_TYPE, targetType)
             .set(MonthlyMetric.TARGET_ID, targetId)
             .set(MonthlyMetric.NAME, name)
             .set(MonthlyMetric.VALUE, value)
             .set(MonthlyMetric.YEAR, year)
             .set(MonthlyMetric.MONTH, month)
             .into(Fact.class);
    }

    @Override
    protected void createDailyMetric(String targetType,
                                     String targetId,
                                     String name,
                                     int year,
                                     int month,
                                     int day,
                                     int value) {
        mongo.insert()
             .set(DailyMetric.TARGET_TYPE, targetType)
             .set(DailyMetric.TARGET_ID, targetId)
             .set(DailyMetric.NAME, name)
             .set(DailyMetric.VALUE, value)
             .set(DailyMetric.YEAR, year)
             .set(DailyMetric.MONTH, month)
             .set(DailyMetric.DAY, day)
             .into(Fact.class);
    }

    @Override
    public int queryFact(String targetType, String targetId, String name) {
        return mango.select(Fact.class)
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
        return mango.select(Fact.class)
                    .eq(Fact.TARGET_TYPE, targetType)
                    .eq(Fact.TARGET_ID, targetId)
                    .fields(Fact.NAME, Fact.VALUE)
                    .queryList()
                    .stream()
                    .collect(Collectors.toMap(Fact::getName, Fact::getValue));
    }

    @Override
    protected int queryMetric(Class<? extends MongoEntity> table,
                              String targetType,
                              String targetId,
                              String name,
                              Integer year,
                              Integer month,
                              Integer day) {
        return mango.select(table)
                    .fields(Fact.VALUE)
                    .eq(Fact.TARGET_TYPE, targetType)
                    .eq(Fact.TARGET_ID, targetId)
                    .eq(Fact.NAME, name)
                    .eqIgnoreNull(YearlyMetric.YEAR, year)
                    .eqIgnoreNull(MonthlyMetric.MONTH, month)
                    .eqIgnoreNull(DailyMetric.DAY, day)
                    .first()
                    .map(entity -> ((Fact) entity).getValue())
                    .orElse(0);
    }
}
