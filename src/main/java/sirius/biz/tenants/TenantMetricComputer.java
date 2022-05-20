/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.metrics.MonthlyMetricComputer;
import sirius.db.mixing.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Aggregates the user activity metrics and performance flags for each {@link Tenant}.
 * <p>
 * This computes the metrics:
 * <ul>
 *  <li><tt>num-users</tt>: Total number of users.</li>
 *  <li><tt>num-active-users</tt>: Total number of <tt>active</tt> users.</li>
 *  <li><tt>avg-activity</tt>: Average activity level of all active users.</li>
 *  <li><tt>avg-education-level</tt>: Average education level level of all active users.</li>
 * </ul>
 * <p>
 * Also, two performance flags are maintained: <tt>active-users</tt> for tenants which have at least one active user.
 * and <tt>academy-users</tt> for tenants which have at least one academy user.
 *
 * @param <T> the actual entity type being handled by this class
 * @see UserAccountActivityMetricComputer
 * @see UserAccountAcademyMetricComputer
 */
public abstract class TenantMetricComputer<T extends BaseEntity<?> & Tenant<?>> extends MonthlyMetricComputer<T> {

    /**
     * Contains the total number of users per tenant.
     */
    public static final String METRIC_NUM_USERS = "num-users";

    /**
     * Contains the number of active users per tenant.
     */
    public static final String METRIC_NUM_ACTIVE_USERS = "num-active-users";

    /**
     * Contains the average activity level of all active users.
     */
    public static final String METRIC_AVG_ACTIVITY = "avg-activity";

    /**
     * Contains the average education level of all active users.
     */
    public static final String METRIC_AVG_EDUCATION_LEVEL = "avg-education-level";

    @Override
    public int getLevel() {
        // We need to await the results of UserAccountActivityMetricComputer and UserAccountAcademyMetricComputer...
        return 1;
    }

    @Override
    public void compute(LocalDate date,
                        LocalDateTime startOfPeriod,
                        LocalDateTime endOfPeriod,
                        boolean pastDate,
                        T tenant) throws Exception {
        if (pastDate) {
            // This is an actual observation and not calculated from recorded data. Therefore, we cannot compute this
            // for past dates...
            return;
        }

        AtomicInteger totalUsers = new AtomicInteger(0);
        AtomicInteger activeUsers = new AtomicInteger(0);
        AtomicInteger sumActivity = new AtomicInteger(0);
        AtomicInteger sumEducationLevel = new AtomicInteger(0);
        AtomicBoolean hasAcademyUsers = new AtomicBoolean(false);

        callForEachUser(tenant, user -> {
            totalUsers.incrementAndGet();

            if (user.getPerformanceData().isSet(getActiveUserFlag())) {
                activeUsers.incrementAndGet();
                sumActivity.addAndGet(metrics.query()
                                             .monthly(UserAccountActivityMetricComputer.METRIC_USER_ACTIVITY)
                                             .of(user.getTypeName(), user.getIdAsString())
                                             .lastValue());
                if (user.getPerformanceData().isSet(getAcademyUserFlag())) {
                    sumEducationLevel.addAndGet(metrics.query()
                                                       .monthly(UserAccountAcademyMetricComputer.METRIC_USER_EDUCATION_LEVEL)
                                                       .of(user.getTypeName(), user.getIdAsString())
                                                       .lastValue());
                    hasAcademyUsers.set(true);
                }
            }
        });

        metrics.updateMonthlyMetric(tenant, METRIC_NUM_USERS, date, totalUsers.get());
        tenant.getPerformanceData().modify().set(getAcademyUsersFlag(), hasAcademyUsers.get()).commit();

        if (activeUsers.get() > 0) {
            metrics.updateMonthlyMetric(tenant, METRIC_NUM_ACTIVE_USERS, date, activeUsers.get());
            metrics.updateMonthlyMetric(tenant, METRIC_AVG_ACTIVITY, date, sumActivity.get() / activeUsers.get());
            metrics.updateMonthlyMetric(tenant,
                                        METRIC_AVG_EDUCATION_LEVEL,
                                        date,
                                        sumEducationLevel.get() / activeUsers.get());

            tenant.getPerformanceData().modify().set(getActiveUsersFlag(), activeUsers.get() > 0).commit();
        }
    }


    protected abstract PerformanceFlag getActiveUserFlag();

    protected abstract PerformanceFlag getAcademyUserFlag();

    protected abstract PerformanceFlag getActiveUsersFlag();

    protected abstract PerformanceFlag getAcademyUsersFlag();

    protected abstract void callForEachUser(T tenant, Consumer<UserAccount<?, T>> user);
}
