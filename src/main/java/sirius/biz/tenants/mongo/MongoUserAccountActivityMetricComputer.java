/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.tenants.UserAccountActivityMetricComputer;
import sirius.kernel.di.std.Register;

/**
 * Measures the activity index for all {@link MongoUserAccount user accounts}.
 */
@Register
public class MongoUserAccountActivityMetricComputer extends UserAccountActivityMetricComputer<MongoUserAccount> {

    public static final PerformanceFlag ACTIVE_USER =
            PerformanceFlag.register(MongoUserAccount.class, "active-user", 0).makeVisible().markAsFilter();
    public static final PerformanceFlag FREQUENT_USER =
            PerformanceFlag.register(MongoUserAccount.class, "frequent-user", 1).makeVisible().markAsFilter();

    @Override
    public Class<MongoUserAccount> getType() {
        return MongoUserAccount.class;
    }

    @Override
    protected PerformanceFlag getActiveUserFlag() {
        return ACTIVE_USER;
    }

    @Override
    protected PerformanceFlag getFrequentUserFlag() {
        return FREQUENT_USER;
    }
}
