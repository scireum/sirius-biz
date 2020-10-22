/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.AutoSetup;
import sirius.kernel.AutoSetupRule;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;

/**
 * Creates an initial tenant and user if none are available.
 */
@Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoTenantAutoSetup implements AutoSetupRule {

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

    @Override
    public void setup() throws Exception {
        if (mango.select(MongoTenant.class).exists()) {
            return;
        }
        if (mango.select(MongoUserAccount.class).exists()) {
            return;
        }

        AutoSetup.LOG.INFO("Creating system tenant....");
        MongoTenant tenant = new MongoTenant();
        tenant.getTenantData().setName("System Tenant");
        mango.update(tenant);

        // Fix the ID of the system tenant to be "1" - This should normally be avoided
        // at all cost, but in this case it greatly simplifies the system config...
        mongo.update().where(MongoEntity.ID, tenant.getId()).set(MongoEntity.ID, "1").executeFor(MongoTenant.class);
        tenant = mango.find(MongoTenant.class, "1")
                      .orElseThrow(() -> new IllegalStateException(
                              "Failed to resolve the system tenant after changing its ID!"));

        AutoSetup.LOG.INFO("Creating user 'system' with password 'system'....");
        MongoUserAccount ua = new MongoUserAccount();
        ua.getTenant().setValue(tenant);
        ua.getUserAccountData().setEmail("system@localhost.local");
        ua.getUserAccountData().getLogin().setUsername("system");
        ua.getUserAccountData().getLogin().setCleartextPassword("system");
        ua.getTrace().setSilent(true);

        // This should be enough to grant us more roles via the UI
        ua.getUserAccountData().getPermissions().getPermissions().add("administrator");
        ua.getUserAccountData().getPermissions().getPermissions().add("user-administrator");
        ua.getUserAccountData().getPermissions().getPermissions().add("system-administrator");
        mango.update(ua);
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
