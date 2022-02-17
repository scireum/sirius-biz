/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.mongo.MongoPerformanceData;
import sirius.biz.analytics.metrics.mongo.MongoMonthlyGlobalMetricComputer;
import sirius.biz.model.LoginData;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.tenants.jdbc.SQLTenants;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provides some global metrics for {@link MongoTenant MongoDB based tenants}.
 */
@Register(framework=SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class MongoTenantGlobalMetricComputer extends MongoMonthlyGlobalMetricComputer {

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
    private Mango mango;

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
                                          (int) mango.selectFromSecondary(MongoTenant.class).count());
        metrics.updateGlobalMonthlyMetric(METRIC_NUM_ACTIVE_TENANTS,
                                          date,
                                          (int) mango.selectFromSecondary(MongoTenant.class)
                                                     .where(MongoPerformanceData.filterFlagSet(MongoTenantMetricComputer.ACTIVE_USERS))
                                                     .count());
        metrics.updateGlobalMonthlyMetric(METRIC_NUM_USERS,
                                          date,
                                          (int) mango.selectFromSecondary(MongoTenant.class)
                                                     .eq(MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                         .inner(LoginData.ACCOUNT_LOCKED),
                                                         false)
                                                     .count());
        metrics.updateGlobalMonthlyMetric(METRIC_NUM_ACTIVE_USERS,
                                          date,
                                          (int) mango.selectFromSecondary(MongoTenant.class)
                                                     .eq(MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                         .inner(LoginData.ACCOUNT_LOCKED),
                                                         false)
                                                     .where(MongoPerformanceData.filterFlagSet(
                                                             MongoUserAccountActivityMetricComputer.ACTIVE_USER))
                                                     .count());
    }
}
