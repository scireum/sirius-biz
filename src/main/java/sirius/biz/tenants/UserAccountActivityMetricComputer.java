/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.metrics.MonthlyMetricComputer;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;

import java.time.LocalDate;

/**
 * Base class to compute the user activity metric and performance flags for user accounts.
 * <p>
 * This computes the <tt>user-activity</tt> metric which is the percentage of days in the last
 * {@link UserAccountActivityMetricComputer#observationPeriodDays N days} in which the user was seen by the
 * {@link TenantUserManager}.
 * <p>
 * Also two performance flags are maintained: <tt>active-user</tt> for users which are seen at least
 * {@link UserAccountActivityMetricComputer#minDaysForActiveUsers} times and <tt>frequent-user</tt>
 * for users which are seen at least {@link UserAccountActivityMetricComputer#minDaysForFrequentUsers} times.
 *
 * @param <U> the actual entity type being handled by this class
 */
public abstract class UserAccountActivityMetricComputer<U extends BaseEntity<?> & UserAccount<?, ?>>
        extends MonthlyMetricComputer<U> {

    /**
     * Contains the name of the monthly metric which is recorded for every user.
     * <p>
     * This metric contains the percentage of the last {@link #observationPeriodDays} days in which the user
     * was seen.
     */
    private static final String METRIC_USER_ACTIVITY = "user-activity";

    /**
     * Contains the number of days used to compute the user activity metric.
     * <p>
     * If the computation would be interpreted as sliding window approach, this would define the size / length of
     * the window.
     */
    @ConfigValue("analytics.user-accounts.observationPeriodDays")
    private int observationPeriodDays;

    /**
     * Contains the minimum number of this in the {@link #observationPeriodDays} so that the user is considered
     * as "active".
     */
    @ConfigValue("analytics.user-accounts.minDaysForActiveUsers")
    private int minDaysForActiveUsers;

    /**
     * Contains the minimum number of this in the {@link #observationPeriodDays} so that the user is considered
     * as "frequent user".
     */
    @ConfigValue("analytics.user-accounts.minDaysForFrequentUsers")
    private int minDaysForFrequentUsers;

    @Part
    private EventRecorder eventRecorder;

    @Override
    public void compute(LocalDate date, U entity) throws Exception {
        LocalDate lowerLimit = date.minusDays(observationPeriodDays);

        int numberOfActiveDays = eventRecorder.getDatabase()
                                              .createQuery("SELECT COUNT(DISTINCT eventDate) AS numberOfDays"
                                                           + " FROM useractivityevent"
                                                           + " WHERE userData_userId = ${userId}"
                                                           + "   AND eventDate > ${lowerLimit}"
                                                           + "   AND eventDate <= ${upperLimit}")
                                              .set("userId", entity.getUniqueName())
                                              .set("lowerLimit", lowerLimit)
                                              .set("upperLimit", date)
                                              .first()
                                              .flatMap(row -> row.getValue("numberOfDays").asOptionalInt())
                                              .orElse(0);

        int activityRateInPercent = numberOfActiveDays * 100 / observationPeriodDays;
        metrics.updateMonthlyMetric(entity, METRIC_USER_ACTIVITY, date, activityRateInPercent);

        if (date.getMonthValue() == LocalDate.now().getMonthValue()) {
            entity.getPerformanceData()
                  .modify()
                  .set(getActiveUserFlag(), numberOfActiveDays >= minDaysForActiveUsers)
                  .set(getFrequentUserFlag(), numberOfActiveDays >= minDaysForFrequentUsers)
                  .commit();
        }
    }

    protected abstract PerformanceFlag getActiveUserFlag();

    protected abstract PerformanceFlag getFrequentUserFlag();
}
