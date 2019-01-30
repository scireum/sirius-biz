/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.tenants.Tenant;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.health.Exceptions;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Base class which marks subclasses as aware of their tenant they belong to.
 */
public interface TenantAware {

    Mapping TENANT = Mapping.named("tenant");

    /**
     * Returns the tenant id as string.
     *
     * @return the tenant id as string
     */
    String getTenantAsString();

    BaseEntityRef<?,?> getTenant();


    /**
     * Asserts that the given object has the same tenant as this object.
     *
     * @param fieldLabel the field in which the referenced object would be stored - used to generate an appropriate
     *                   error message
     * @param other      the object to check
     */
    default void assertSameTenant(Supplier<String> fieldLabel, TenantAware other) {
        if (other != null && (!Objects.equals(other.getTenantAsString(), getTenantAsString()))) {
            throw Exceptions.createHandled()
                            .withNLSKey("TenantAware.invalidTenant")
                            .set("field", fieldLabel.get())
                            .handle();
        }
    }

    /**
     * Installs the currently present tenant (in the {@link sirius.web.security.UserContext}) into this entity.
     */
    void fillWithCurrentTenant();

    void withTenant(Tenant<?> tenant);


}
