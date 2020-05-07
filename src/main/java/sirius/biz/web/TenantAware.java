/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Explain;

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
    void assertSameTenant(Supplier<String> fieldLabel, TenantAware other);

    /**
     * Installs the currently present tenant (in the {@link sirius.web.security.UserContext}) into this entity.
     */
    void fillWithCurrentTenant();

    /**
     * Installs the currently present tenant (in the {@link sirius.web.security.UserContext}) into this entity.
     * <p>
     * However, if there is already a tenant present in the entity, it is asserted, that the tenant remains
     * the same.
     */
    void setOrVerifyCurrentTenant();
}
