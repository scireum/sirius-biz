/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.tenants.BaseTenantAutoSetup;
import sirius.biz.tenants.TenantData;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.Mixing;
import sirius.kernel.AutoSetup;
import sirius.kernel.AutoSetupRule;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

/**
 * Creates an initial tenant and user if none are available.
 * <p>
 * Also, if SAML settings are present in the system config, the system tenant is updated accordingly. This can
 * be used to distribute SAML settings via the system configuration and thus use configuration management tools
 * like puppet.
 */
@Register(classes = AutoSetupRule.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLTenantAutoSetup extends BaseTenantAutoSetup {

    @Part
    private OMA oma;

    @Part
    private Mixing mixing;

    @Override
    public void setup() throws Exception {
        if (oma.select(SQLTenant.class).exists()) {
            updateSystemTenantIfNecessary();
            return;
        }
        if (oma.select(SQLUserAccount.class).exists()) {
            return;
        }

        AutoSetup.LOG.INFO("Creating system tenant....");

        // The ID of the future system tenant will be set to "1" or another fixed value from the configuration files.
        // This should normally be avoided at all cost, but in this case, it greatly simplifies the system config...
        long systemTenantId = 1;
        if (tenants != null) {
            systemTenantId = Long.parseLong(tenants.getSystemTenantId());
        }

        // We can not modify the ID of a new tenant entity directly. Thus, we create an empty tenant with the proper ID
        // using low-level techniques first, just to be filled properly in an instant.
        oma.getDatabase(Mixing.DEFAULT_REALM)
           .insertRow(mixing.getDescriptor(SQLTenant.class).getRelationName(),
                      Context.create()
                             .set(SQLTenant.ID.getName(), systemTenantId)
                             .set(SQLTenant.TENANT_DATA.inner(TenantData.NAME).getName(), ""));

        // We load the newly created empty tenant via high-level techniques, making use of default values set in the
        // entity
        SQLTenant tenant = oma.select(SQLTenant.class)
                              .where(OMA.FILTERS.eq(SQLTenant.ID, systemTenantId))
                              .one()
                              .orElseThrow(() -> {
                                  return Exceptions.createHandled().withSystemErrorMessage("oh nej").handle();
                              });

        // Having a semi-initialised tenant, it is now time to set it up as system tenant
        setupTenantData(tenant);
        oma.update(tenant);

        // We only create the system user, if no SAML settings are present...
        if (Strings.isEmpty(tenant.getTenantData().getSamlRequestIssuerName())) {
            AutoSetup.LOG.INFO("Creating user 'system' with password 'system'....");
            SQLUserAccount userAccount = new SQLUserAccount();
            userAccount.getTenant().setValue(tenant);
            setupUserData(userAccount);
            oma.update(userAccount);
        }
    }

    private void updateSystemTenantIfNecessary() {
        if (tenants == null) {
            return;
        }

        SQLTenant tenant = oma.find(SQLTenant.class, tenants.getSystemTenantId()).orElse(null);
        if (tenant != null) {
            updateSamlData(tenant);
        }

        if (tenant.isAnyMappingChanged()) {
            AutoSetup.LOG.INFO(
                    "Updating the SAML settings of the system tenant using the data from the system config...");
            oma.update(tenant);
        }
    }
}
