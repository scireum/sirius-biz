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
import java.util.List;
import java.util.Map;

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
     * Creates or updates a global yearly metric.
     *
     * @param name  the name of the metric
     * @param year  the year of the metric
     * @param value the value to store
     */
    void updateGlobalYearlyMetric(String name, int year, int value);

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
     * Creates or updates a global monthly metric.
     *
     * @param name  the name of the metric
     * @param year  the year of the metric
     * @param month the month of the metric
     * @param value the value to store
     */
    void updateGlobalMonthlyMetric(String name, int year, int month, int value);

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
     * Creates or updates a daily monthly metric.
     *
     * @param name  the name of the metric
     * @param year  the year of the metric
     * @param month the month of the metric
     * @param day   the day of the metric
     * @param value the value to store
     */
    void updateGlobalDailyMetric(String name, int year, int month, int day, int value);

    /**
     * Queries a fact for the given target with the given name.
     *
     * @param targetType the type of the object to fetch the metric for
     * @param targetId   the id of the object to fetch the metric for
     * @param name       the name of the metric
     * @return the stored value or 0 of no value is present
     */
    int queryFact(String targetType, String targetId, String name);

    /**
     * Queries a fact for the given target with the given name.
     *
     * @param target the object to fetch the metric for
     * @param name   the name of the metric
     * @return the stored value or 0 of no value is present
     */
    int queryFact(BaseEntity<?> target, String name);

    /**
     * Queries a global fact with the given name.
     *
     * @param name the name of the metric
     * @return the stored value or 0 of no value is present
     */
    int queryGlobalFact(String name);

    /**
     * Queries all facts for the given target.
     *
     * @param targetType the type of the object to fetch the metric for
     * @param targetId   the id of the object to fetch the metric for
     * @return a map containing all known facts for the given target
     */
    Map<String, Integer> queryFacts(String targetType, String targetId);

    /**
     * Queries all facts for the given target.
     *
     * @param target the object to fetch the metric for
     * @return a map containing all known facts for the given target
     */
    Map<String, Integer> queryFacts(BaseEntity<?> target);

    /**
     * Queries all global facts.
     *
     * @return a map containing all known global facts
     */
    Map<String, Integer> queryGlobalFacts();

    /**
     * Queries all yearly metric values for the given target, name and period.
     *
     * @param targetType the type of the object to fetch the metrics for
     * @param targetId   the id of the object to fetch the metrics for
     * @param name       the name of the metric
     * @param from       the start of the period
     * @param to         the end of the period
     * @return a list of metric values for the requested period. Missing values are replaced with 0.
     */
    List<Integer> queryYearlyMetrics(String targetType, String targetId, String name, LocalDate from, LocalDate to);

    /**
     * Queries all yearly metric values for the given target, name and period.
     * <p>
     * Note that this will only return up to {@link BasicMetrics#MAX_YEARLY_METRICS} values.
     *
     * @param target the object to fetch the metrics for
     * @param name   the name of the metric
     * @param from   the start of the period
     * @param to     the end of the period
     * @return a list of metric values for the requested period. Missing values are replaced with 0.
     */
    List<Integer> queryYearlyMetrics(BaseEntity<?> target, String name, LocalDate from, LocalDate to);

    /**
     * Queries all yearly metric values for the given global metric and period.
     * <p>
     * Note that this will only return up to {@link BasicMetrics#MAX_YEARLY_METRICS} values.
     *
     * @param name the name of the metric
     * @param from the start of the period
     * @param to   the end of the period
     * @return a list of metric values for the requested period. Missing values are replaced with 0.
     */
    List<Integer> queryGlobalYearlyMetrics(String name, LocalDate from, LocalDate to);

    /**
     * Queries all monthly metric values for the given target, name and period.
     * <p>
     * Note that this will only return up to {@link BasicMetrics#MAX_MONTHLY_METRICS} values.
     *
     * @param targetType the type of the object to fetch the metrics for
     * @param targetId   the id of the object to fetch the metrics for
     * @param name       the name of the metric
     * @param from       the start of the period
     * @param to         the end of the period
     * @return a list of metric values for the requested period. Missing values are replaced with 0.
     */
    List<Integer> queryMonthlyMetrics(String targetType, String targetId, String name, LocalDate from, LocalDate to);

    /**
     * Queries all monthly metric values for the given target, name and period.
     * <p>
     * Note that this will only return up to {@link BasicMetrics#MAX_MONTHLY_METRICS} values.
     *
     * @param target the object to fetch the metrics for
     * @param name   the name of the metric
     * @param from   the start of the period
     * @param to     the end of the period
     * @return a list of metric values for the requested period. Missing values are replaced with 0.
     */
    List<Integer> queryMonthlyMetrics(BaseEntity<?> target, String name, LocalDate from, LocalDate to);

    /**
     * Queries all monthly metric values for the given global metric and period.
     * <p>
     * Note that this will only return up to {@link BasicMetrics#MAX_MONTHLY_METRICS} values.
     *
     * @param name the name of the metric
     * @param from the start of the period
     * @param to   the end of the period
     * @return a list of metric values for the requested period. Missing values are replaced with 0.
     */
    List<Integer> queryGlobalMonthlyMetrics(String name, LocalDate from, LocalDate to);

    /**
     * Queries all daily metric values for the given target, name and period.
     * <p>
     * Note that this will only return up to {@link BasicMetrics#MAX_DAILY_METRICS} values.
     *
     * @param targetType the type of the object to fetch the metrics for
     * @param targetId   the id of the object to fetch the metrics for
     * @param name       the name of the metric
     * @param from       the start of the period
     * @param to         the end of the period
     * @return a list of metric values for the requested period. Missing values are replaced with 0.
     */
    List<Integer> queryDailyMetrics(String targetType, String targetId, String name, LocalDate from, LocalDate to);

    /**
     * Queries all daily metric values for the given target, name and period.
     * <p>
     * Note that this will only return up to {@link BasicMetrics#MAX_DAILY_METRICS} values.
     *
     * @param target the object to fetch the metrics for
     * @param name   the name of the metric
     * @param from   the start of the period
     * @param to     the end of the period
     * @return a list of metric values for the requested period. Missing values are replaced with 0.
     */
    List<Integer> queryDailyMetrics(BaseEntity<?> target, String name, LocalDate from, LocalDate to);

    /**
     * Queries all daily metric values for the given global metric and period.
     * <p>
     * Note that this will only return up to {@link BasicMetrics#MAX_DAILY_METRICS} values.
     *
     * @param name the name of the metric
     * @param from the start of the period
     * @param to   the end of the period
     * @return a list of metric values for the requested period. Missing values are replaced with 0.
     */
    List<Integer> queryGlobalDailyMetrics(String name, LocalDate from, LocalDate to);
}
