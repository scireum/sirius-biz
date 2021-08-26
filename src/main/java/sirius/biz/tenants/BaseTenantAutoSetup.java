/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.kernel.AutoSetupRule;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;

/**
 * Contains the common base functionality of {@link sirius.biz.tenants.jdbc.SQLTenantAutoSetup} and
 * {@link sirius.biz.tenants.mongo.MongoTenantAutoSetup}.
 */
public abstract class BaseTenantAutoSetup implements AutoSetupRule {

    @Part
    protected Tenants<?, ?, ?> tenants;

    @ConfigValue("security.system-saml.requestIssuerName")
    private String samlRequestIssuerName;
    @ConfigValue("security.system-saml.issuerUrl")
    private String samlIssuerUrl;
    @ConfigValue("security.system-saml.issuerIndex")
    private String samlIssuerIndex;
    @ConfigValue("security.system-saml.issuerName")
    private String samlIssuerName;
    @ConfigValue("security.system-saml.fingerprint")
    private String samlFingerprint;

    protected void setupUserData(UserAccount<?, ?> ua) {
        ua.getUserAccountData().setEmail("system@localhost.local");
        ua.getUserAccountData().getLogin().setUsername("system");
        ua.getUserAccountData().getLogin().setCleartextPassword("system");
        ua.getTrace().setSilent(true);
        // This should be enough to grant us more roles via the UI
        ua.getUserAccountData().getPermissions().getPermissions().add("administrator");
        ua.getUserAccountData().getPermissions().getPermissions().add("user-administrator");
        ua.getUserAccountData().getPermissions().getPermissions().add("system-administrator");
    }

    protected void setupTenantData(Tenant<?> tenant) {
        updateSamlData(tenant);
        tenant.getTenantData().setName("System Tenant");
    }

    protected void updateSamlData(Tenant<?> tenant) {
        if (Strings.isFilled(samlRequestIssuerName)) {
            tenant.getTenantData().setSamlRequestIssuerName(samlRequestIssuerName);
        }
        if (Strings.isFilled(samlIssuerUrl)) {
            tenant.getTenantData().setSamlIssuerUrl(samlIssuerUrl);
        }
        if (Strings.isFilled(samlIssuerIndex)) {
            tenant.getTenantData().setSamlIssuerIndex(samlIssuerIndex);
        }
        if (Strings.isFilled(samlFingerprint)) {
            tenant.getTenantData().setSamlFingerprint(samlFingerprint);
        }
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
