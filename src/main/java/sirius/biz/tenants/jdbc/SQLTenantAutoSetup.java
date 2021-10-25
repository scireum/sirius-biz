/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.tenants.BaseTenantAutoSetup;
import sirius.db.jdbc.OMA;
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
@Register(classes = AutoSetupRule.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLTenantAutoSetup extends BaseTenantAutoSetup {

    @Part
    private OMA oma;

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
        SQLTenant tenant = new SQLTenant();
        setupTenantData(tenant);
        oma.update(tenant);

        if (tenants != null) {
            try {
                oma.updateStatement(SQLTenant.class)
                   .set(SQLTenant.ID, Long.parseLong(tenants.getSystemTenantId()))
                   .where(SQLTenant.ID, tenant.getId())
                   .executeUpdate();
            } catch (Exception e) {
                AutoSetup.LOG.WARN("Failed to update ID of system tenant from %s to %s: %s",
                                   tenant.getId(),
                                   tenants.getSystemTenantId(),
                                   e.getMessage());
            }
        }

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
