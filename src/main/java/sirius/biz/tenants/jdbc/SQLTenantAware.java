/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.jdbc.BizEntity;
import sirius.biz.tenants.Tenant;
import sirius.biz.web.TenantAware;
import sirius.db.jdbc.SQLEntityRef;
import sirius.kernel.di.std.Part;

import java.util.Optional;

/**
 * Base class which marks subclasses as aware of their tenant they belong to.
 */
public abstract class SQLTenantAware extends BizEntity implements TenantAware {

    @Part
    private static SQLTenants tenants;

    /**
     * Contains the tenant the entity belongs to.
     */
    private final SQLEntityRef<SQLTenant> tenant = SQLEntityRef.writeOnceOn(SQLTenant.class, SQLEntityRef.OnDelete.REJECT);

    @Override
    public SQLEntityRef<SQLTenant> getTenant() {
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
    public void setOrVerifyCurrentTenant() {
        if (getTenant().isEmpty()) {
            fillWithCurrentTenant();
        } else {
            tenants.assertTenant(this);
        }
    }

    @Override
    public void withTenant(Tenant<?> tenant) {
        getTenant().setValue((SQLTenant) tenant);
    }

    @Override
    public SQLTenant fetchCachedRequiredTenant() {
        return tenants.fetchCachedRequiredTenant(tenant);
    }

    @Override
    public Optional<SQLTenant> fetchCachedTenant() {
        return tenants.fetchCachedTenant(tenant);
    }
}
