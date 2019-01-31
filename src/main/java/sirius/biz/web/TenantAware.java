/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.tenants.Tenant;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Explain;
import sirius.kernel.health.Exceptions;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Base class which marks subclasses as aware of their tenant they belong to.
 */
@SuppressWarnings("squid:S1214")
@Explain("We rather keep the constants here, as this emulates the behaviour and layout of a real enttiy.")
public interface TenantAware {

    /**
     * Defines the mapping which stores the tenant to which this user belongs.
     */
    Mapping TENANT = Mapping.named("tenant");

    /**
     * Returns the tenant id as string.
     *
     * @return the tenant id as string
     */
    String getTenantAsString();

    /**
     * Returns the reference to the tenant to which this entity belongs.
     *
     * @return the tenant as reference which owns the entity
     */
    BaseEntityRef<?, ?> getTenant();

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

    /**
     * Fills the tenant with the given one.
     *
     * @param tenant the tnenat to set for this entity
     */
    void withTenant(Tenant<?> tenant);
}
