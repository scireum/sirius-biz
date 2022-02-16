/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.model.LoginData;
import sirius.biz.tenants.TenantMetricComputer;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.function.Consumer;

/**
 * Provides some metrics for {@link MongoTenant}.
 */
@Register
public class MongoTenantMetricComputer extends TenantMetricComputer<MongoTenant> {

    /**
     * Marks tenants with active users.
     */
    public static final PerformanceFlag ACTIVE_USERS =
            PerformanceFlag.register(MongoTenant.class, "active-users", 0).makeVisible().markAsFilter();

    /**
     * Marks tenants which hase users that use the video academy.
     */
    public static final PerformanceFlag ACADEMY_USERS =
            PerformanceFlag.register(MongoTenant.class, "academy-users", 1).makeVisible().markAsFilter();

    @Part
    private Mango mango;

    @Override
    protected PerformanceFlag getAcademyUsersFlag() {
        return ACADEMY_USERS;
    }

    @Override
    protected PerformanceFlag getActiveUsersFlag() {
        return ACTIVE_USERS;
    }

    @Override
    protected void callForEachUser(MongoTenant tenant, Consumer<UserAccount<?, MongoTenant>> user) {
        mango.selectFromSecondary(MongoUserAccount.class)
             .eq(MongoUserAccount.TENANT, tenant)
             .eq(MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.ACCOUNT_LOCKED), false)
             .streamBlockwise()
             .forEach(user);
    }

    @Override
    public Class<MongoTenant> getType() {
        return MongoTenant.class;
    }
}
