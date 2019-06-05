/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.mongo.MongoBizEntity;
import sirius.biz.protocol.JournalData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

import java.util.Set;
import java.util.TreeSet;

/**
 * Reprensents the MongoDB implementation of {@link Tenant}.
 */
@Framework(MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoTenant extends MongoBizEntity implements Tenant<String> {

    /**
     * Contains the parent tenant of this tenant.
     */
    @Autoloaded
    @NullAllowed
    private final MongoRef<MongoTenant> parent = MongoRef.on(MongoTenant.class, MongoRef.OnDelete.SET_NULL);

    public static final Mapping TENANT_DATA = Mapping.named("tenantData");
    private final TenantData tenantData = new TenantData(this);

    /**
     * Used to record changes on fields of the user.
     */
    public static final Mapping JOURNAL = Mapping.named("journal");
    private final JournalData journal = new JournalData(this);

    /**
     * Contains the effectively enabled features / permissions for this tenant.
     */
    @Transient
    private Set<String> effectivePermissions;

    @Part
    private static Tenants<?, ?, ?> tenants;

    @BeforeSave
    protected void enhanceSearchField() {
        addContent(getTenantData().getAddress().getStreet());
        addContent(getTenantData().getAddress().getZip());
        addContent(getTenantData().getAddress().getCity());
    }

    @Override
    public boolean hasPermission(String permission) {
        if (effectivePermissions == null) {
            Set<String> permissions = new TreeSet<>(getTenantData().getPackageData().computeExpandedPermissions());
            if (Strings.areEqual(getIdAsString(), tenants.getTenantUserManager().getSystemTenantId())) {
                permissions.add(TenantUserManager.PERMISSION_SYSTEM_TENANT);
            }

            if (Strings.isFilled(tenantData.getAccountNumber())) {
                permissions.add("tenant-" + tenantData.getAccountNumber());
            }

            effectivePermissions = permissions;
        }

        return effectivePermissions.contains(permission);
    }

    @Override
    public MongoRef<MongoTenant> getParent() {
        return parent;
    }

    @Override
    public TenantData getTenantData() {
        return tenantData;
    }

    @Override
    public JournalData getJournal() {
        return journal;
    }

    @Override
    public String toString() {
        return getTenantData().toString();
    }

    @Override
    public String getRateLimitScope() {
        return getIdAsString();
    }
}
