/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.tenants.BaseTenantAutoSetup;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.AutoSetup;
import sirius.kernel.AutoSetupRule;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Creates an initial tenant and user if none are available.
 * <p>
 * Also, if SAML settings are present in the system config, the system tenant is updated accordingly. This can
 * be used to distribute SAML settings via the system configuration and thus use configuration management tools
 * like puppet.
 */
@Register(classes = AutoSetupRule.class, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoTenantAutoSetup extends BaseTenantAutoSetup {

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

    @Override
    public void setup() throws Exception {
        if (mango.select(MongoTenant.class).exists()) {
            updateSystemTenantIfNecessary();
            return;
        }
        if (mango.select(MongoUserAccount.class).exists()) {
            return;
        }

        AutoSetup.LOG.INFO("Creating system tenant....");

        // The ID of the future system tenant will be set to "1" or another fixed value from the configuration files.
        // This should normally be avoided at all cost, but in this case, it greatly simplifies the system config...
        String systemTenantId = "1";
        if (tenants != null) {
            systemTenantId = tenants.getSystemTenantId();
        }

        // We can not modify the ID of a new tenant entity directly. Thus, we create an empty tenant with the proper ID
        // using low-level techniques first, just to be filled properly in an instant.
        mongo.insert().set(MongoEntity.ID, systemTenantId).into(MongoTenant.class);

        // We load the newly created empty tenant via high-level techniques, making use of default values set in the
        // entity
        MongoTenant tenant = mango.find(MongoTenant.class, systemTenantId)
                                  .orElseThrow(() -> new IllegalStateException(
                                          "Failed to resolve the system tenant after creation!"));

        // Having a semi-initialised tenant, it is now time to set it up as system tenant
        setupTenantData(tenant);
        mango.update(tenant);

        // We only create the system user, if no SAML settings are present...
        if (Strings.isEmpty(tenant.getTenantData().getSamlRequestIssuerName())) {
            AutoSetup.LOG.INFO("Creating user 'system' with password 'system'....");
            MongoUserAccount userAccount = new MongoUserAccount();
            userAccount.getTenant().setValue(tenant);
            setupUserData(userAccount);
            mango.update(userAccount);
        }
    }

    private void updateSystemTenantIfNecessary() {
        if (tenants == null) {
            return;
        }

        MongoTenant tenant = mango.find(MongoTenant.class, tenants.getSystemTenantId()).orElse(null);
        if (tenant != null) {
            updateSamlData(tenant);
            if (tenant.isAnyMappingChanged()) {
                AutoSetup.LOG.INFO(
                        "Updating the SAML settings of the system tenant using the data from the system config...");
                mango.update(tenant);
            }
        }
    }
}
