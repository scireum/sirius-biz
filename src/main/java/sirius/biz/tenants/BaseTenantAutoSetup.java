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
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Priorized;

import java.util.function.Consumer;

/**
 * Contains the common base functionality of {@link sirius.biz.tenants.jdbc.SQLTenantAutoSetup} and
 * {@link sirius.biz.tenants.mongo.MongoTenantAutoSetup}.
 */
public abstract class BaseTenantAutoSetup implements AutoSetupRule {

    @Part
    protected Tenants<?, ?, ?> tenants;

    @Parts(TenantAutoSetupExtender.class)
    protected PartCollection<TenantAutoSetupExtender> extenders;

    @ConfigValue("security.system-saml.externalLoginIntervalDays")
    private Integer externalLoginIntervalDays;
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

    protected void setupUserData(UserAccount<?, ?> userAccount) {
        userAccount.getUserAccountData().setEmail("system@localhost.local");
        userAccount.getUserAccountData().getLogin().setUsername("system");
        userAccount.getUserAccountData().getLogin().setCleartextPassword("system");
        userAccount.getTrace().setSilent(true);

        // This should be enough to grant us more roles via the UI
        userAccount.getUserAccountData().getPermissions().getPermissions().add("administrator");
        userAccount.getUserAccountData().getPermissions().getPermissions().add("user-administrator");
        userAccount.getUserAccountData().getPermissions().getPermissions().add("system-administrator");

        extenders.forEach(extender -> extender.enhanceUser(userAccount));
    }

    protected void setupTenantData(Tenant<?> tenant) {
        updateSamlData(tenant);
        tenant.getTenantData().setName("System Tenant");
        tenant.getTenantData().setFullName("System Tenant");

        extenders.forEach(extender -> extender.enhanceTenant(tenant));
    }

    protected void updateSamlData(Tenant<?> tenant) {
        tenant.getTenantData().setExternalLoginIntervalDays(externalLoginIntervalDays);

        acceptIfFilled(samlIssuerName, tenant.getTenantData()::setSamlIssuerName);
        acceptIfFilled(samlRequestIssuerName, tenant.getTenantData()::setSamlRequestIssuerName);
        acceptIfFilled(samlIssuerUrl, tenant.getTenantData()::setSamlIssuerUrl);
        acceptIfFilled(samlIssuerIndex, tenant.getTenantData()::setSamlIssuerIndex);
        acceptIfFilled(samlFingerprint, tenant.getTenantData()::setSamlFingerprint);
    }

    private void acceptIfFilled(String value, Consumer<String> filler) {
        if (Strings.isFilled(value)) {
            filler.accept(value);
        }
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
