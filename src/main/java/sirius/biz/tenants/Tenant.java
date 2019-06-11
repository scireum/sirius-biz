/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.isenguard.RateLimitedEntity;
import sirius.biz.protocol.Journaled;
import sirius.biz.protocol.Traced;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.transformers.Transformable;

/**
 * Provides the database independent interface for describing a tenant which uses the system.
 * <p>
 * Note that all fields are represented via {@link TenantData}.
 *
 * @param <I> the type used to represent database IDs
 */
@SuppressWarnings("squid:S1214")
@Explain("We rather keep the constants here, as this emulates the behaviour and layout of a real enttiy.")
public interface Tenant<I> extends Transformable, Traced, Journaled, RateLimitedEntity {

    /**
     * Contains the mapping used to identify the parent tenant of the current one.
     */
    Mapping PARENT = Mapping.named("parent");

    /**
     * Contains the effective fields which are mapped by the appropriate mapper depending on the actual entity type.
     */
    Mapping TENANT_DATA = Mapping.named("tenantData");

    /**
     * Returns the reference to the parent tenant (if available).
     *
     * @return the reference to lookup the parent tenant
     */
    BaseEntityRef<I, ? extends Tenant<I>> getParent();

    /**
     * Provides access to the effective tenant data.
     *
     * @return the tenant data composite which stores all values in a database independent manner.
     */
    TenantData getTenantData();

    /**
     * Returns a string representation of the entity ID.
     * <p>
     * If the entity is new, "new" will be returned.
     *
     * @return the entity ID as string or "new" if the entity {@link #isNew()}.
     */
    String getIdAsString();

    /**
     * Returns an unique name of this entity.
     * <p>
     * This unique string representation of this entity is made up of its type along with its id.
     *
     * @return an unique representation of this entity or an empty string if the entity was not written to the
     * database yet
     */
    String getUniqueName();

    /**
     * Determines if the entity is new (not yet written to the database).
     *
     * @return <tt>true</tt> if the entity has not been written to the database yes, <tt>false</tt> otherwise
     */
    boolean isNew();

    /**
     * Determines if this tenant has the requested permission.
     * <p>
     * Note that this only verifies that the tenant has a specific property or feature enabled but does not
     * take user permissions into account. These can be checked using
     * {@link sirius.web.security.UserInfo#hasPermission(String)}.
     *
     * @param permission the permission to check
     * @return <tt>true</tt> if the tenant has the permission, <tt>false</tt> otherwise
     */
    boolean hasPermission(String permission);
}
