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
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.di.std.Part;

/**
 * Base class which marks subclasses as aware of their tenant they belong to.
 */
public abstract class MongoTenantAware extends MongoBizEntity implements TenantAware {

    @Part
    private static MongoTenants tenants;

    /**
     * Contains the tenant the entity belongs to.
     */
    private final MongoRef<MongoTenant> tenant = MongoRef.on(MongoTenant.class, MongoRef.OnDelete.CASCADE);

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
    public void withTenant(Tenant<?> tenant) {
        getTenant().setValue((MongoTenant) tenant);
    }
}
