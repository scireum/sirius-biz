/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.metrics.MonthlyMetricComputer;
import sirius.biz.tenants.UserAccountActivityMetricComputer;
import sirius.kernel.di.std.Register;

/**
 * Provides the actual <tt>user-activity</tt> computer for {@link SQLUserAccount}.
 */
@Register
public class SQLUserAccountActivityMetricComputer extends UserAccountActivityMetricComputer<SQLUserAccount> {

    @Override
    public Class<SQLUserAccount> getType() {
        return SQLUserAccount.class;
    }

    /**
     * Marks active users.
     */
    public static final PerformanceFlag ACTIVE_USER =
            PerformanceFlag.register(SQLUserAccount.class, "active-user", 0).makeVisible().markAsFilter();

    /**
     * Marks users that "frequently" use the application.
     */
    public static final PerformanceFlag FREQUENT_USER =
            PerformanceFlag.register(SQLUserAccount.class, "frequent-user", 1).makeVisible().markAsFilter();

    @Override
    protected PerformanceFlag getActiveUserFlag() {
        return ACTIVE_USER;
    }

    @Override
    protected PerformanceFlag getFrequentUserFlag() {
        return FREQUENT_USER;
    }
}
