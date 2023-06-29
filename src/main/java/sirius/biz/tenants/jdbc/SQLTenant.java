/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.flags.jdbc.SQLPerformanceData;
import sirius.biz.importer.AutoImport;
import sirius.biz.jdbc.BizEntity;
import sirius.biz.protocol.JournalData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.biz.web.Autoloaded;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents the JDBC/SQL implementation of {@link Tenant}.
 */
@Framework(SQLTenants.FRAMEWORK_TENANTS_JDBC)
@TranslationSource(Tenant.class)
public class SQLTenant extends BizEntity implements Tenant<Long> {

    /**
     * Contains the parent tenant of this tenant.
     */
    @Autoloaded
    @AutoImport
    @NullAllowed
    private final SQLEntityRef<SQLTenant> parent = SQLEntityRef.on(SQLTenant.class, SQLEntityRef.OnDelete.SET_NULL);

    public static final Mapping TENANT_DATA = Mapping.named("tenantData");
    private final TenantData tenantData = new TenantData(this);

    private final SQLPerformanceData performanceData = new SQLPerformanceData(this);

    /**
     * Contains the effectively enabled features / permissions for this tenant.
     */
    @Transient
    private Set<String> effectivePermissions;

    @Part
    @Nullable
    private static Tenants<?, ?, ?> tenants;

    @Override
    public boolean hasPermission(String permission) {
        return getPermissions().contains(permission);
    }

    @Override
    public Set<String> getPermissions() {
        if (effectivePermissions == null) {
            Set<String> permissions = new TreeSet<>(getTenantData().getPackageData().computeExpandedPermissions());
            if (Strings.areEqual(getIdAsString(), tenants.getTenantUserManager().getSystemTenantId())) {
                permissions.add(PERMISSION_SYSTEM_TENANT);
            }

            if (Strings.isFilled(tenantData.getAccountNumber())) {
                permissions.add("tenant-" + tenantData.getAccountNumber());
            }

            // We assign here, in case an AdditionalRolesProvider uses hasPermission recursively...
            effectivePermissions = permissions;

            // and we assign again the final value after all AdditionalRolesProviders ran. Note that this is a
            // NOOP if no AdditionalRolesProviders are present...
            effectivePermissions = TenantUserManager.computeEffectiveTenantPermissions(this, effectivePermissions);
        }

        return Collections.unmodifiableSet(effectivePermissions);
    }

    @Override
    public SQLEntityRef<SQLTenant> getParent() {
        return parent;
    }

    @Override
    public TenantData getTenantData() {
        return tenantData;
    }

    @Override
    public JournalData getJournal() {
        return tenantData.getJournal();
    }

    @Override
    public String toString() {
        return getTenantData().toString();
    }

    @Override
    public String getRateLimitScope() {
        return getIdAsString();
    }

    @Override
    public SQLPerformanceData getPerformanceData() {
        return performanceData;
    }
}
