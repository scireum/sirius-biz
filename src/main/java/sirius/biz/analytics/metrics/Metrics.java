/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.db.mixing.BaseEntity;

import java.time.LocalDate;

/**
 * Provides a database independent API to store and retrieve metrics.
 * <p>
 * Metrics in this case are numerical values (integers) which are stored for an object in serveral time intervals.
 * <p>
 * Most metrics are most probably computed using a {@link DailyMetricComputer} or {@link MonthlyMetricComputer}.
 * However this API can also be used outside of such computers.
 */
public interface Metrics {

    /**
     * Creates or updates a fact (timeless metric) for the given target object.
     *
     * @param targetType the type of the object for which the metric is stored
     * @param targetId   the id of the object for which the metric is stored
     * @param name       the name of the metric
     * @param value      the value to store
     */
    void updateFact(String targetType, String targetId, String name, int value);

    /**
     * Creates or updates a fact (timeless metric) for the given target object.
     *
     * @param target the object for which the metric is stored
     * @param name   the name of the metric
     * @param value  the value to store
     */
    void updateFact(BaseEntity<?> target, String name, int value);

    /**
     * Creates or updates a global fact (timeless metric).
     *
     * @param name  the name of the metric
     * @param value the value to store
     */
    void updateGlobalFact(String name, int value);

    /**
     * Creates or updates a yearly metric for the given target object.
     *
     * @param targetType the type of the object for which the metric is stored
     * @param targetId   the id of the object for which the metric is stored
     * @param name       the name of the metric
     * @param year       the year of the metric
     * @param value      the value to store
     */
    void updateYearlyMetric(String targetType, String targetId, String name, int year, int value);

    /**
     * Creates or updates a yearly metric for the given target object.
     *
     * @param target the object for which the metric is stored
     * @param name   the name of the metric
     * @param year   the year of the metric
     * @param value  the value to store
     */
    void updateYearlyMetric(BaseEntity<?> target, String name, int year, int value);

    /**
     * Creates or updates a yearly metric for the given target object.
     *
     * @param target the object for which the metric is stored
     * @param name   the name of the metric
     * @param date   the date to determine the year of the metric
     * @param value  the value to store
     */
    void updateYearlyMetric(BaseEntity<?> target, String name, LocalDate date, int value);

    /**
     * Creates or updates a global yearly metric.
     *
     * @param name  the name of the metric
     * @param year  the year of the metric
     * @param value the value to store
     */
    void updateGlobalYearlyMetric(String name, int year, int value);

    /**
     * Creates or updates a global yearly metric.
     *
     * @param name  the name of the metric
     * @param date  the date to determine the year of the metric
     * @param value the value to store
     */
    void updateGlobalYearlyMetric(String name, LocalDate date, int value);

    /**
     * Creates or updates a monthly metric for the given target object.
     *
     * @param targetType the type of the object for which the metric is stored
     * @param targetId   the id of the object for which the metric is stored
     * @param name       the name of the metric
     * @param year       the year of the metric
     * @param month      the month of the metric
     * @param value      the value to store
     */
    void updateMonthlyMetric(String targetType, String targetId, String name, int year, int month, int value);

    /**
     * Creates or updates a monthly metric for the given target object.
     *
     * @param target the object for which the metric is stored
     * @param name   the name of the metric
     * @param year   the year of the metric
     * @param month  the month of the metric
     * @param value  the value to store
     */
    void updateMonthlyMetric(BaseEntity<?> target, String name, int year, int month, int value);

    /**
     * Creates or updates a monthly metric for the given target object.
     *
     * @param target the object for which the metric is stored
     * @param name   the name of the metric
     * @param date   the date to determine the year and month of the metric
     * @param value  the value to store
     */
    void updateMonthlyMetric(BaseEntity<?> target, String name, LocalDate date, int value);

    /**
     * Creates or updates a global monthly metric.
     *
     * @param name  the name of the metric
     * @param year  the year of the metric
     * @param month the month of the metric
     * @param value the value to store
     */
    void updateGlobalMonthlyMetric(String name, int year, int month, int value);

    /**
     * Creates or updates a global monthly metric.
     *
     * @param name  the name of the metric
     * @param date  the date to determine the year and month of the metric
     * @param value the value to store
     */
    void updateGlobalMonthlyMetric(String name, LocalDate date, int value);

    /**
     * Creates or updates a daily metric for the given target object.
     *
     * @param targetType the type of the object for which the metric is stored
     * @param targetId   the id of the object for which the metric is stored
     * @param name       the name of the metric
     * @param year       the year of the metric
     * @param month      the month of the metric
     * @param day        the day of the metric
     * @param value      the value to store
     */
    void updateDailyMetric(String targetType, String targetId, String name, int year, int month, int day, int value);

    /**
     * Creates or updates a daily metric for the given target object.
     *
     * @param target the object for which the metric is stored
     * @param name   the name of the metric
     * @param year   the year of the metric
     * @param month  the month of the metric
     * @param day    the day of the metric
     * @param value  the value to store
     */
    void updateDailyMetric(BaseEntity<?> target, String name, int year, int month, int day, int value);

    /**
     * Creates or updates a daily metric for the given target object.
     *
     * @param target the object for which the metric is stored
     * @param name   the name of the metric
     * @param date   the date to determine the year, month and day of the metric
     * @param value  the value to store
     */
    void updateDailyMetric(BaseEntity<?> target, String name, LocalDate date, int value);

    /**
     * Creates or updates a daily metric.
     *
     * @param name  the name of the metric
     * @param year  the year of the metric
     * @param month the month of the metric
     * @param day   the day of the metric
     * @param value the value to store
     */
    void updateGlobalDailyMetric(String name, int year, int month, int day, int value);

    /**
     * Creates or updates a daily metric.
     *
     * @param name  the name of the metric
     * @param date  the date to determine the year, month and day of the metric
     * @param value the value to store
     */
    void updateGlobalDailyMetric(String name, LocalDate date, int value);

    /**
     * Creates a query against the metrics database.
     *
     * @return a query to be executed against the metrics database
     */
    MetricQuery query();

    /**
     * Fetches the label for the given metric.
     * <p>
     * This is boilerplate for {@code NLS.get('Metric.'+name)}
     *
     * @param name the name of the metric to fetch the label for
     * @return the label of the metric in the current language
     */
    String fetchLabel(String name);
}
