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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores metrics into the appropriate collections of the underlying MongoDB.
 */
@Register(classes = Metrics.class, framework = MongoMetrics.FRAMEWORK_MONGO_METRICS)
public class MongoMetrics extends BasicMetrics {

    /**
     * Contains the name of the framework which controls whether storing metrics in MongoDB is supported.
     */
    public static final String FRAMEWORK_MONGO_METRICS = "biz.analytics-metrics-mongo";

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

    /**
     * Deletes the given metric if it exists.
     *
     * @param type       the type of metric to delete
     * @param targetType the target type to delete
     * @param targetId   the id of the target for which the metric is to be deleted
     * @param name       the name of the metric to delete
     * @param year       the year (if available) of the metric to delete
     * @param month      the month (if available) of the metric to delete
     * @param day        the day (if available) of the metric to delete
     */
    private void delete(Class<? extends MongoEntity> type,
                        String targetType,
                        String targetId,
                        String name,
                        Integer year,
                        Integer month,
                        Integer day) {
        Deleter deleteQuery = mongo.delete()
                                   .where(Fact.TARGET_TYPE, targetType)
                                   .where(Fact.TARGET_ID, targetId)
                                   .where(Fact.NAME, name);
        if (year != null) {
            deleteQuery.where(YearlyMetric.YEAR, year);
        }
        if (month != null) {
            deleteQuery.where(MonthlyMetric.MONTH, month);
        }
        if (day != null) {
            deleteQuery.where(DailyMetric.DAY, day);
        }
        deleteQuery.singleFrom(type);
    }

    /**
     * Updates the given metric if it exists.
     *
     * @param type       the type of metric to update
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
    private boolean update(Class<? extends MongoEntity> type,
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
                                   .where(Fact.NAME, name);
        if (year != null) {
            updateQuery.where(YearlyMetric.YEAR, year);
        }
        if (month != null) {
            updateQuery.where(MonthlyMetric.MONTH, month);
        }
        if (day != null) {
            updateQuery.where(DailyMetric.DAY, day);
        }

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

    /**
     * Executes the update for the given metric.
     * <p>
     * If the given value is 0, the metric is deleted. Otherwise we attempt to update an existing metric value. If this
     * doesn't exist a new one will be created.
     *
     * @param type       the type of metric to insert
     * @param targetType the target type to insert
     * @param targetId   the id of the target for which the metric is to be inserted
     * @param value      the value to store
     * @param name       the name of the metric to insert
     * @param year       the year (if available) of the metric to insert
     * @param month      the month (if available) of the metric to insert
     * @param day        the day (if available) of the metric to insert
     */
    @SuppressWarnings("squid:S00107")
    @Explain("We rather have 8 parameters here and keep the logic properly encapsulated")
    protected void execute(Class<? extends MongoEntity> type,
                           String targetType,
                           String targetId,
                           String name,
                           int value,
                           Integer year,
                           Integer month,
                           Integer day) {
        if (value == 0) {
            delete(type, targetType, targetId, name, year, month, day);
            return;
        }

        if (update(type, targetType, targetId, name, value, year, month, day)) {
            return;
        }

        insert(type, targetType, targetId, name, value, year, month, day);
    }

    @Override
    public void updateFact(String targetType, String targetId, String name, int value) {
        execute(Fact.class, targetType, targetId, name, value, null, null, null);
    }

    @Override
    public void updateYearlyMetric(String targetType, String targetId, String name, int year, int value) {
        execute(Fact.class, targetType, targetId, name, value, year, null, null);
    }

    @Override
    public void updateMonthlyMetric(String targetType, String targetId, String name, int year, int month, int value) {
        execute(Fact.class, targetType, targetId, name, value, year, month, null);
    }

    @Override
    public void updateDailyMetric(String targetType,
                                  String targetId,
                                  String name,
                                  int year,
                                  int month,
                                  int day,
                                  int value) {
        execute(Fact.class, targetType, targetId, name, value, year, month, day);
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
    public List<Integer> queryYearlyMetrics(String targetType,
                                            String targetId,
                                            String name,
                                            LocalDate from,
                                            LocalDate to) {
        List<Integer> result = new ArrayList<>();
        LocalDate date = from;
        while (!date.isAfter(to)) {
            result.add(mango.select(YearlyMetric.class)
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
            result.add(mango.select(MonthlyMetric.class)
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
            result.add(mango.select(DailyMetric.class)
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
