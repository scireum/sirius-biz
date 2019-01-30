/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.db.jdbc.OMA;
import sirius.kernel.AutoSetup;
import sirius.kernel.AutoSetupRule;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;

@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLTenantAutoSetup implements AutoSetupRule {

    @Part
    private OMA oma;

    @Override
    public void setup() throws Exception {
        if (oma.select(SQLTenant.class).exists()) {
            return;
        }
        if (oma.select(SQLUserAccount.class).exists()) {
            return;
        }

        AutoSetup.LOG.INFO("Creating system tenant....");
        SQLTenant tenant = new SQLTenant();
        tenant.getTenantData().setName("System Tenant");
        oma.update(tenant);

        AutoSetup.LOG.INFO("Creating user 'system' with password 'system'....");
        SQLUserAccount ua = new SQLUserAccount();
        ua.getTenant().setValue(tenant);
        ua.getUserAccountData().setEmail("system@localhost.local");
        ua.getUserAccountData().getLogin().setUsername("system");
        ua.getUserAccountData().getLogin().setCleartextPassword("system");
        ua.getTrace().setSilent(true);
        // This should be enough to grant us more roles via the UI
        ua.getUserAccountData().getPermissions().getPermissions().add("administrator");
        ua.getUserAccountData().getPermissions().getPermissions().add("user-administrator");
        oma.update(ua);
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
