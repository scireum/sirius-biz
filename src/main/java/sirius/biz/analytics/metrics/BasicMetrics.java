/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Explain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class which handles all the database independent boilerplate.
 *
 * @param <E> the entity type used by a concrete subclass
 */
public abstract class BasicMetrics<E extends BaseEntity<?>> implements Metrics {

    /**
     * Used as fake reference name for global metrics.
     * <p>
     * Most of the metrics are associated to a database entity and will store their type and id as reference.
     * For global metrics we fill both fields with the value to store and retrieve them.
     */
    private static final String GLOBAL = "global";

    /**
     * Returns the entity type used to store facts.
     *
     * @return the class which represents facts
     */
    protected abstract Class<? extends E> getFactType();

    /**
     * Returns the entity type used to store yearly metrics.
     *
     * @return the class which represents yearly metrics
     */
    protected abstract Class<? extends E> getYearlyMetricType();

    /**
     * Returns the entity type used to store monthly metrics.
     *
     * @return the class which represents monthly metrics
     */
    protected abstract Class<? extends E> getMonthlyMetricType();

    /**
     * Returns the entity type used to store daily metrics.
     *
     * @return the class which represents daily metrics
     */
    protected abstract Class<? extends E> getDailyMetricType();

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
    protected abstract void deleteMetric(Class<? extends E> table,
                                         String targetType,
                                         String targetId,
                                         String name,
                                         Integer year,
                                         Integer month,
                                         Integer day);

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
    protected abstract boolean updateMetric(Class<? extends E> table,
                                            String targetType,
                                            String targetId,
                                            String name,
                                            int value,
                                            Integer year,
                                            Integer month,
                                            Integer day);

    /**
     * Queries the given metric.
     *
     * @param table      the table to query
     * @param targetType the target type to query
     * @param targetId   the id of the target to query for
     * @param name       the name of the metric to query
     * @param year       the year (if available) of the metric to query
     * @param month      the month (if available) of the metric to query
     * @param day        the day (if available) of the metric to query
     * @return the value available for the given parameters
     */
    protected abstract int queryMetric(Class<? extends E> table,
                                       String targetType,
                                       String targetId,
                                       String name,
                                       Integer year,
                                       Integer month,
                                       Integer day);

    @Override
    public void updateGlobalFact(String name, int value) {
        updateFact(GLOBAL, GLOBAL, name, value);
    }

    @Override
    public void updateFact(BaseEntity<?> target, String name, int value) {
        if (!target.isNew()) {
            updateFact(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, value);
        }
    }

    @Override
    public void updateFact(String targetType, String targetId, String name, int value) {
        if (value == 0) {
            deleteMetric(getFactType(), targetType, targetId, name, null, null, null);
            return;
        }

        if (updateMetric(getFactType(), targetType, targetId, name, value, null, null, null)) {
            return;
        }

        createFact(targetType, targetId, name, value);
    }

    /**
     * Creates a new fact for the given parameters.
     *
     * @param targetType the type of the object for which the metric is stored
     * @param targetId   the id of the object for which the metric is stored
     * @param name       the name of the metric
     * @param value      the value to store
     */
    protected abstract void createFact(String targetType, String targetId, String name, int value);

    @Override
    public void updateGlobalYearlyMetric(String name, int year, int value) {
        updateYearlyMetric(GLOBAL, GLOBAL, name, year, value);
    }

    @Override
    public void updateYearlyMetric(BaseEntity<?> target, String name, int year, int value) {
        if (!target.isNew()) {
            updateYearlyMetric(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, year, value);
        }
    }

    @Override
    public void updateYearlyMetric(String targetType, String targetId, String name, int year, int value) {
        if (value == 0) {
            deleteMetric(getYearlyMetricType(), targetType, targetId, name, year, null, null);
            return;
        }

        if (updateMetric(getYearlyMetricType(), targetType, targetId, name, value, year, null, null)) {
            return;
        }

        createYearlyMetric(targetType, targetId, name, value, year);
    }

    /**
     * Creates a new yearly metric for the given parameters.
     *
     * @param targetType the type of the object for which the metric is stored
     * @param targetId   the id of the object for which the metric is stored
     * @param name       the name of the metric
     * @param value      the value to store
     * @param year       the year of the metric
     */
    protected abstract void createYearlyMetric(String targetType, String targetId, String name, int value, int year);

    @Override
    public void updateGlobalMonthlyMetric(String name, int year, int month, int value) {
        updateMonthlyMetric(GLOBAL, GLOBAL, name, year, month, value);
    }

    @Override
    public void updateMonthlyMetric(BaseEntity<?> target, String name, int year, int month, int value) {
        if (!target.isNew()) {
            updateMonthlyMetric(Mixing.getNameForType(target.getClass()),
                                target.getIdAsString(),
                                name,
                                year,
                                month,
                                value);
        }
    }

    @Override
    public void updateMonthlyMetric(String targetType, String targetId, String name, int year, int month, int value) {
        if (value == 0) {
            deleteMetric(getMonthlyMetricType(), targetType, targetId, name, year, month, null);
            return;
        }

        if (updateMetric(getMonthlyMetricType(), targetType, targetId, name, value, year, month, null)) {
            return;
        }

        createMonthlyMetric(targetType, targetId, name, year, month, value);
    }

    /**
     * Creates a new monthly metric for the given parameters.
     *
     * @param targetType the type of the object for which the metric is stored
     * @param targetId   the id of the object for which the metric is stored
     * @param name       the name of the metric
     * @param value      the value to store
     * @param year       the year of the metric
     * @param month      the month of the metric
     */
    protected abstract void createMonthlyMetric(String targetType,
                                                String targetId,
                                                String name,
                                                int year,
                                                int month,
                                                int value);

    @Override
    public void updateGlobalDailyMetric(String name, int year, int month, int day, int value) {
        updateDailyMetric(GLOBAL, GLOBAL, name, year, month, day, value);
    }

    @Override
    public void updateDailyMetric(BaseEntity<?> target, String name, int year, int month, int day, int value) {
        if (!target.isNew()) {
            updateDailyMetric(Mixing.getNameForType(target.getClass()),
                              target.getIdAsString(),
                              name,
                              year,
                              month,
                              day,
                              value);
        }
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
            deleteMetric(getDailyMetricType(), targetType, targetId, name, year, month, day);
            return;
        }

        if (updateMetric(getDailyMetricType(), targetType, targetId, name, value, year, month, day)) {
            return;
        }

        createDailyMetric(targetType, targetId, name, year, month, day, value);
    }

    /**
     * Creates a new daily metric for the given parameters.
     *
     * @param targetType the type of the object for which the metric is stored
     * @param targetId   the id of the object for which the metric is stored
     * @param name       the name of the metric
     * @param value      the value to store
     * @param year       the year of the metric
     * @param month      the month of the metric
     * @param day        the day of the metric
     */
    protected abstract void createDailyMetric(String targetType,
                                              String targetId,
                                              String name,
                                              int year,
                                              int month,
                                              int day,
                                              int value);

    @Override
    public int queryFact(BaseEntity<?> target, String name) {
        return queryFact(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name);
    }

    @Override
    public int queryGlobalFact(String name) {
        return queryFact(GLOBAL, GLOBAL, name);
    }

    @Override
    public Map<String, Integer> queryFacts(BaseEntity<?> target) {
        return queryFacts(Mixing.getNameForType(target.getClass()), target.getIdAsString());
    }

    @Override
    public Map<String, Integer> queryGlobalFacts() {
        return queryFacts(GLOBAL, GLOBAL);
    }

    @Override
    public List<Integer> queryYearlyMetrics(BaseEntity<?> target, String name, LocalDate from, LocalDate to) {
        return queryYearlyMetrics(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, from, to);
    }

    @Override
    public List<Integer> queryGlobalYearlyMetrics(String name, LocalDate from, LocalDate to) {
        return queryYearlyMetrics(GLOBAL, GLOBAL, name, from, to);
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
            result.add(queryMetric(getYearlyMetricType(), targetType, targetId, name, date.getYear(), null, null));
            date = date.plusYears(1);
        }

        return result;
    }

    @Override
    public List<Integer> queryMonthlyMetrics(BaseEntity<?> target, String name, LocalDate from, LocalDate to) {
        return queryMonthlyMetrics(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, from, to);
    }

    @Override
    public List<Integer> queryGlobalMonthlyMetrics(String name, LocalDate from, LocalDate to) {
        return queryMonthlyMetrics(GLOBAL, GLOBAL, name, from, to);
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
            result.add(queryMetric(getMonthlyMetricType(),
                                   targetType,
                                   targetId,
                                   name,
                                   date.getYear(),
                                   date.getMonthValue(),
                                   null));
            date = date.plusMonths(1);
        }

        return result;
    }

    @Override
    public List<Integer> queryDailyMetrics(BaseEntity<?> target, String name, LocalDate from, LocalDate to) {
        return queryDailyMetrics(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, from, to);
    }

    @Override
    public List<Integer> queryGlobalDailyMetrics(String name, LocalDate from, LocalDate to) {
        return queryDailyMetrics(GLOBAL, GLOBAL, name, from, to);
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
            result.add(queryMetric(getDailyMetricType(),
                                   targetType,
                                   targetId,
                                   name,
                                   date.getYear(),
                                   date.getMonthValue(),
                                   date.getDayOfMonth()));
            date = date.plusDays(1);
        }

        return result;
    }
}
