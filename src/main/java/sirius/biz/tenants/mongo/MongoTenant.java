/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import com.typesafe.config.ConfigFactory;
import sirius.biz.analytics.flags.mongo.MongoPerformanceData;
import sirius.biz.mongo.MongoBizEntity;
import sirius.biz.mongo.SortField;
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
import sirius.kernel.settings.Settings;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Represents the MongoDB implementation of {@link Tenant}.
 */
@Framework(MongoTenants.FRAMEWORK_TENANTS_MONGO)
@TranslationSource(Tenant.class)
@Index(name = "index_prefixes", columns = "searchPrefixes", columnSettings = Mango.INDEX_ASCENDING)
@Index(name = "index_sort", columns = "sortField_sortField", columnSettings = Mango.INDEX_ASCENDING)
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

    public static final Mapping SORT_FIELD = Mapping.named("sortField");
    private final SortField sortField = new SortField(this);

    /**
     * Contains the effectively enabled features / permissions for this tenant.
     */
    @Transient
    private Set<String> effectivePermissions;

    @Transient
    private Settings settings;

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
    public Settings getSettings() {
        if (settings == null) {
            if (null == getTenantData().getConfig()) {
                settings = new Settings(ConfigFactory.empty(), false);
            } else {
                settings = new Settings(getTenantData().getConfig(), false);
            }
        }

        return settings;
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

    public SortField getSortField() {
        return sortField;
    }
}
