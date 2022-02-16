/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.flags.jdbc.SQLPerformanceData;
import sirius.biz.analytics.metrics.MonthlyGlobalMetricComputer;
import sirius.biz.model.LoginData;
import sirius.biz.tenants.UserAccountData;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provides some global metrics for {@link SQLTenant JDBC based tenants}.
 */
@Register
@Framework(SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLGlobalMetricComputer extends MonthlyGlobalMetricComputer {

    /**
     * Contains the total number of tenants.
     */
    public static final String METRIC_NUM_TENANTS = "num-tenants";

    /**
     * Contains the number of tenants with active users.
     */
    public static final String METRIC_NUM_ACTIVE_TENANTS = "num-active-tenants";

    /**
     * Contains the total number of users.
     */
    public static final String METRIC_NUM_USERS = "num-users";

    /**
     * Contains the number of active users.
     */
    public static final String METRIC_NUM_ACTIVE_USERS = "num-active-users";

    @Part
    private OMA oma;

    @Override
    public int getLevel() {
        // We need to await the results of TenantMetricComputer
        return 2;
    }

    @Override
    public void compute(LocalDate date, LocalDateTime startOfPeriod, LocalDateTime endOfPeriod, boolean pastDate)
            throws Exception {
        if (pastDate) {
            // This is an actual observation and not calculated from recorded data. Therefore, we cannot compute this
            // for past dates...
            return;
        }

        metrics.updateGlobalMonthlyMetric(METRIC_NUM_TENANTS,
                                          date,
                                          (int) oma.selectFromSecondary(SQLTenant.class).count());
        metrics.updateGlobalMonthlyMetric(METRIC_NUM_ACTIVE_TENANTS,
                                          date,
                                          (int) oma.selectFromSecondary(SQLTenant.class)
                                                   .where(SQLPerformanceData.filterFlagSet(SQLTenantMetricComputer.ACTIVE_USERS))
                                                   .count());
        metrics.updateGlobalMonthlyMetric(METRIC_NUM_USERS,
                                          date,
                                          (int) oma.selectFromSecondary(SQLUserAccount.class)
                                                   .eq(SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                       .inner(LoginData.ACCOUNT_LOCKED),
                                                       false)
                                                   .count());
        metrics.updateGlobalMonthlyMetric(METRIC_NUM_ACTIVE_USERS,
                                          date,
                                          (int) oma.selectFromSecondary(SQLUserAccount.class)
                                                   .eq(SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                       .inner(LoginData.ACCOUNT_LOCKED),
                                                       false)
                                                   .where(SQLPerformanceData.filterFlagSet(
                                                           SQLUserAccountActivityMetricComputer.ACTIVE_USER))
                                                   .count());
    }
}
