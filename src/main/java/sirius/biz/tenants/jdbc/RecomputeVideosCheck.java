/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.tenants.UserAccountVideosCheck;
import sirius.kernel.di.std.Register;

/**
 * Computes onboarding videos for all {@link SQLUserAccount users}.
 */
@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class RecomputeVideosCheck extends UserAccountVideosCheck<Long, SQLTenant, SQLUserAccount> {

    @Override
    public Class<SQLUserAccount> getType() {
        return SQLUserAccount.class;
    }
}
