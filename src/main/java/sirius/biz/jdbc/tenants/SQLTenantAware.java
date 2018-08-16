/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.tenants;

import sirius.biz.jdbc.model.BizEntity;
import sirius.biz.web.TenantAware;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.kernel.di.std.Part;

/**
 * Base class which marks subclasses as aware of their tenant they belong to.
 */
public abstract class SQLTenantAware extends BizEntity implements TenantAware {

    @Part
    private static Tenants tenants;

    /**
     * Contains the tenant the entity belongs to.
     */
    public static final Mapping TENANT = Mapping.named("tenant");
    private final SQLEntityRef<Tenant> tenant = SQLEntityRef.on(Tenant.class, SQLEntityRef.OnDelete.CASCADE);

    public SQLEntityRef<Tenant> getTenant() {
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
}
