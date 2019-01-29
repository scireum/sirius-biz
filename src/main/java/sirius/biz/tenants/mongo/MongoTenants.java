/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccount;
import sirius.db.jdbc.OMA;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Helps for extract the current {@link UserAccount} and {@link Tenant}.
 * <p>
 * Also some boiler plate methods are provided to perform some assertions.
 */
@Register(classes = {Tenants.class, MongoTenants.class}, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoTenants extends Tenants<String, MongoTenant, MongoUserAccount> {

    /**
     * Names the framework which must be enabled to activate the tenant based user management.
     */
    public static final String FRAMEWORK_TENANTS_MONGO = "biz.tenants-mongo";

    @Part
    private Mango mango;

    @Override
    public Class<MongoTenant> getTenantClass() {
        return MongoTenant.class;
    }

    @Override
    public Class<MongoUserAccount> getUserClass() {
        return MongoUserAccount.class;
    }

    @Override
    protected boolean checkIfHasChildTenants(String tenantId) {
        return mango.select(MongoTenant.class).eq(Tenant.PARENT, tenantId).exists();
    }
}
