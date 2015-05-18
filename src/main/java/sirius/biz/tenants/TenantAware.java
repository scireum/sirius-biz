/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.BizEntity;
import sirius.kernel.health.Exceptions;
import sirius.mixing.Column;
import sirius.mixing.EntityRef;

import java.util.function.Supplier;

/**
 * Created by aha on 07.05.15.
 */
public abstract class TenantAware extends BizEntity {

    private final EntityRef<Tenant> tenant = EntityRef.on(Tenant.class, EntityRef.OnDelete.CASCADE);
    public static final Column TENANT = Column.named("tenant");

    public EntityRef<Tenant> getTenant() {
        return tenant;
    }

    public void assertSameTenant(Supplier<String> fieldLabel, TenantAware other) {
        if (other != null && (other.getTenant().getId() != getTenant().getId())) {
            throw Exceptions.createHandled()
                            .withNLSKey("TenantAware.invalidTenant")
                            .set("field", fieldLabel.get())
                            .handle();
        }
    }

}
