/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.mongo.MongoPerformanceData;
import sirius.biz.mongo.MongoBizEntity;
import sirius.biz.protocol.JournalData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.db.mongo.Mango;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Reprensents the MongoDB implementation of {@link Tenant}.
 */
@Framework(MongoTenants.FRAMEWORK_TENANTS_MONGO)
@TranslationSource(Tenant.class)
@Index(name = "index_prefixes", columns = "searchPrefixes", columnSettings = Mango.INDEX_ASCENDING)
public class MongoTenant extends MongoBizEntity implements Tenant<String> {

    /**
     * Contains the parent tenant of this tenant.
     */
    @Autoloaded
    @NullAllowed
    private final MongoRef<MongoTenant> parent = MongoRef.on(MongoTenant.class, MongoRef.OnDelete.SET_NULL);

    public static final Mapping TENANT_DATA = Mapping.named("tenantData");
    private final TenantData tenantData = new TenantData(this);

    private final MongoPerformanceData performanceData = new MongoPerformanceData(this);

    /**
     * Contains the effectively enabled features / permissions for this tenant.
     */
    @Transient
    private Set<String> effectivePermissions;

    @Part
    @Nullable
    private static Tenants<?, ?, ?> tenants;

    @Override
    protected void addCustomSearchPrefixes(Consumer<String> tokenizer) {
        tokenizer.accept(getTenantData().getAddress().getStreet());
        tokenizer.accept(getTenantData().getAddress().getZip());
        tokenizer.accept(getTenantData().getAddress().getCity());
    }

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

            // We assign here, in case an AdditionalRolesProvider uses hasPermission recursively...
            effectivePermissions = permissions;

            // and we assign again the final value after all AdditionalRolesProviders ran. Note that this is a
            // NOOP if no AdditionalRolesProviders are present...
            effectivePermissions = TenantUserManager.computeEffectiveTenantPermissions(this, effectivePermissions);
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
    public MongoPerformanceData getPerformanceData() {
        return performanceData;
    }
}
