/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.model.LoginData;
import sirius.biz.tenants.TenantMetricComputer;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.function.Consumer;

/**
 * Provides some metrics for {@link SQLTenant}.
 */
@Register
public class SQLTenantMetricComputer extends TenantMetricComputer<SQLTenant> {

    /**
     * Marks tenants with active users.
     */
    public static final PerformanceFlag ACTIVE_USERS =
            PerformanceFlag.register(SQLTenant.class, "active-users", 0).makeVisible().markAsFilter();

    /**
     * Marks tenants which hase users that use the video academy.
     */
    public static final PerformanceFlag ACADEMY_USERS =
            PerformanceFlag.register(SQLUserAccount.class, "academy-users", 1).makeVisible().markAsFilter();

    @Part
    private OMA oma;

    @Override
    protected PerformanceFlag getAcademyUsersFlag() {
        return ACADEMY_USERS;
    }

    @Override
    protected PerformanceFlag getActiveUsersFlag() {
        return ACTIVE_USERS;
    }

    @Override
    protected void callForEachUser(SQLTenant tenant, Consumer<UserAccount<?, SQLTenant>> user) {
        oma.selectFromSecondary(SQLUserAccount.class)
           .eq(SQLUserAccount.TENANT, tenant)
           .eq(SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.ACCOUNT_LOCKED), false)
           .streamBlockwise()
           .forEach(user);
    }

    @Override
    public Class<SQLTenant> getType() {
        return SQLTenant.class;
    }
}
