/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.mongo.MongoBizEntity;
import sirius.biz.tenants.Tenant;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mongo.Mango;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Base class which marks subclasses as aware of their tenant they belong to.
 * <p>
 * Note that an index is automatically created containing the tenant itself and the searchPrefixes,
 * which are added via {@link MongoBizEntity}. You can skip the index creation by defining an {@link Index}
 * with the same name and without columns.
 */
@Index(name = "index_tenant_prefixes",
        columns = {"tenant", "searchPrefixes"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING})
public abstract class MongoTenantAware extends MongoBizEntity implements TenantAware {

    @Part
    @Nullable
    private static MongoTenants tenants;

    @Transient
    private boolean skipTenantCheck;

    /**
     * Contains the tenant the entity belongs to.
     */
    private final MongoRef<MongoTenant> tenant = MongoRef.writeOnceOn(MongoTenant.class, MongoRef.OnDelete.REJECT);

    @Override
    public MongoRef<MongoTenant> getTenant() {
        return tenant;
    }

    @Override
    public String getTenantAsString() {
        return getTenant().isFilled() ? String.valueOf(getTenant().getId()) : null;
    }

    @Override
    public void fillWithCurrentTenant() {
        getTenant().setValue(tenants.getRequiredTenant());
    }

    @Override
    public void assertSameTenant(Supplier<String> fieldLabel, TenantAware other) {
        if (other != null && (!Objects.equals(other.getTenantAsString(), getTenantAsString()))) {
            throw Exceptions.createHandled()
                            .withNLSKey("TenantAware.invalidTenant")
                            .set("field", fieldLabel.get())
                            .handle();
        }
    }

    @Override
    public boolean belongsToCurrentTenant() {
        return getTenant().getId().equals(tenants.getRequiredTenant().getId());
    }

    @Override
    public void skipTenantCheck() {
        this.skipTenantCheck = true;
    }

    @Override
    public void setOrVerifyCurrentTenant() {
        if (getTenant().isEmpty()) {
            fillWithCurrentTenant();
        } else if (!skipTenantCheck) {
            tenants.assertTenant(this);
        }
    }

    /**
     * Fills the tenant with the given one.
     *
     * @param tenant the tenant to set for this entity
     */
    public void withTenant(Tenant<?> tenant) {
        getTenant().setValue((MongoTenant) tenant);
    }

    /**
     * Fetches the tenant from cache or throws an exception if no tenant is present.
     *
     * @return the tenant which this object belongs to
     */
    public MongoTenant fetchCachedRequiredTenant() {
        return tenants.fetchCachedRequiredTenant(tenant);
    }

    /**
     * Fetches the tenant from cache wrapped in an Optional.
     *
     * @return the optional tenant which this object belongs to
     */
    public Optional<MongoTenant> fetchCachedTenant() {
        return tenants.fetchCachedTenant(tenant);
    }
}
