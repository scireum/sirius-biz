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
import sirius.db.KeyGenerator;
import sirius.db.mongo.Deleter;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.Updater;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.Map;
import java.util.Optional;
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

    @Part
    private KeyGenerator keyGenerator;

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

    @Override
    protected void createFact(String targetType, String targetId, String name, int value) {
        mongo.insert()
             .set(Fact.ID, keyGenerator.generateId())
             .set(Fact.TARGET_TYPE, targetType)
             .set(Fact.TARGET_ID, targetId)
             .set(Fact.NAME, name)
             .set(Fact.VALUE, value)
             .into(Fact.class);
    }

    @Override
    protected void createYearlyMetric(String targetType, String targetId, String name, int value, int year) {
        mongo.insert()
             .set(YearlyMetric.ID, keyGenerator.generateId())
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
             .set(MonthlyMetric.ID, keyGenerator.generateId())
             .set(MonthlyMetric.TARGET_TYPE, targetType)
             .set(MonthlyMetric.TARGET_ID, targetId)
             .set(MonthlyMetric.NAME, name)
             .set(MonthlyMetric.VALUE, value)
             .set(MonthlyMetric.YEAR, year)
             .set(MonthlyMetric.MONTH, month)
             .into(MonthlyMetric.class);
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
             .set(DailyMetric.ID, keyGenerator.generateId())
             .set(DailyMetric.TARGET_TYPE, targetType)
             .set(DailyMetric.TARGET_ID, targetId)
             .set(DailyMetric.NAME, name)
             .set(DailyMetric.VALUE, value)
             .set(DailyMetric.YEAR, year)
             .set(DailyMetric.MONTH, month)
             .set(DailyMetric.DAY, day)
             .into(DailyMetric.class);
    }

    @Override
    protected Optional<Integer> queryMetric(Class<? extends MongoEntity> table,
                                            String targetType,
                                            String targetId,
                                            String name,
                                            Integer year,
                                            Integer month,
                                            Integer day) {
        return mongo.find()
                    .where(Fact.TARGET_TYPE, targetType)
                    .where(Fact.TARGET_ID, targetId)
                    .where(Fact.NAME, name)
                    .whereIgnoreNull(YearlyMetric.YEAR, year)
                    .whereIgnoreNull(MonthlyMetric.MONTH, month)
                    .whereIgnoreNull(DailyMetric.DAY, day)
                    .singleIn(table)
                    .map(doc -> doc.get(Fact.VALUE).asInt(0));
    }
}
