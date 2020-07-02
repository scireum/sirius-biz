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

import java.util.Set;
import java.util.TreeSet;

/**
 * Reprensents the JDBC/SQL implementation of {@link Tenant}.
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
    private static Tenants<?, ?, ?> tenants;

    @Override
    public boolean hasPermission(String permission) {
        if (effectivePermissions == null) {
            Set<String> permissions = new TreeSet<>(getTenantData().getPackageData().computeExpandedPermissions());
            if (Strings.areEqual(getIdAsString(), tenants.getTenantUserManager().getSystemTenantId())) {
                permissions.add(PERMISSION_SYSTEM_TENANT);
            }

            if (Strings.isFilled(tenantData.getAccountNumber())) {
                permissions.add("tenant-" + tenantData.getAccountNumber());
            }

            effectivePermissions = permissions;
        }

        return effectivePermissions.contains(permission);
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
