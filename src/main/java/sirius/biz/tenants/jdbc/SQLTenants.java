/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccount;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Helps to extract the current {@link UserAccount} and {@link Tenant}.
 * <p>
 * Also some boiler plate methods are provided to perform some assertions.
 */
@Register(classes = {Tenants.class, SQLTenants.class}, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLTenants extends Tenants<Long, SQLTenant, SQLUserAccount> {

    /**
     * Names the framework which must be enabled to activate the tenant based user management.
     */
    public static final String FRAMEWORK_TENANTS_JDBC = "biz.tenants-jdbc";

    @Part
    private OMA oma;

    @Override
    public Class<SQLTenant> getTenantClass() {
        return SQLTenant.class;
    }

    @Override
    public Class<SQLUserAccount> getUserClass() {
        return SQLUserAccount.class;
    }

    @Override
    protected boolean checkIfHasChildTenants(Long tenantId) {
        return oma.select(SQLTenant.class).eq(Tenant.PARENT, tenantId).exists();
    }
}
