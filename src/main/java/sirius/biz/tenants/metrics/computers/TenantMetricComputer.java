/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.metrics.computers;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.metrics.MetricComputerContext;
import sirius.biz.analytics.metrics.MonthlyMetricComputer;
import sirius.biz.process.Processes;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.UserAccount;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
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
 *  <li><tt>num-processes</tt></tt>: The number of started processes.</li>
 *  <li><tt>process-duration</tt>: The total computation time (in minutes) for the started processes.</li>
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

    /**
     * Contains the number of processes that the tenant startet within a month.
     */
    public static final String METRIC_NUM_PROCESSES = "num-processes";

    /**
     * Contains the total computation time (in minutes) for the completed processes of the given tenant in a month.
     */
    public static final String METRIC_PROCESS_DURATION = "process-duration";

    @Part
    @Nullable
    private Processes processes;

    @Override
    public int getLevel() {
        // We need to await the results of UserAccountActivityMetricComputer and UserAccountAcademyMetricComputer...
        return 1;
    }

    @Override
    public void compute(MetricComputerContext context, T tenant) throws Exception {
        if (context.periodOutsideOfCurrentInterest()) {
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

        metrics.updateMonthlyMetric(tenant, METRIC_NUM_USERS, context.date(), totalUsers.get());
        tenant.getPerformanceData().modify().set(getAcademyUsersFlag(), hasAcademyUsers.get()).commit();

        if (activeUsers.get() > 0) {
            metrics.updateMonthlyMetric(tenant, METRIC_NUM_ACTIVE_USERS, context.date(), activeUsers.get());
            metrics.updateMonthlyMetric(tenant,
                                        METRIC_AVG_ACTIVITY,
                                        context.date(),
                                        sumActivity.get() / activeUsers.get());
            metrics.updateMonthlyMetric(tenant,
                                        METRIC_AVG_EDUCATION_LEVEL,
                                        context.date(),
                                        sumEducationLevel.get() / activeUsers.get());

            tenant.getPerformanceData().modify().set(getActiveUsersFlag(), activeUsers.get() > 0).commit();
        }

        if (processes != null) {
            Tuple<Integer, Integer> processMetrics =
                    processes.computeProcessMetrics(context.startOfPeriodAsDate(), context.endOfPeriodAsDate(), tenant);
            metrics.updateMonthlyMetric(tenant, METRIC_NUM_PROCESSES, context.date(), processMetrics.getFirst());
            metrics.updateMonthlyMetric(tenant, METRIC_PROCESS_DURATION, context.date(), processMetrics.getSecond());
        }
    }

    protected abstract PerformanceFlag getActiveUserFlag();

    protected abstract PerformanceFlag getAcademyUserFlag();

    protected abstract PerformanceFlag getActiveUsersFlag();

    protected abstract PerformanceFlag getAcademyUsersFlag();

    protected abstract void callForEachUser(T tenant, Consumer<UserAccount<?, T>> user);
}
