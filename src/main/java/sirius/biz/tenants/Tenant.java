/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.analytics.flags.PerformanceFlagged;
import sirius.biz.isenguard.RateLimitedEntity;
import sirius.biz.protocol.Journaled;
import sirius.biz.protocol.Traced;
import sirius.db.mixing.Entity;
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
@Explain("We rather keep the constants here, as this emulates the behaviour and layout of a real entity.")
public interface Tenant<I> extends Entity, Transformable, Traced, Journaled, RateLimitedEntity, PerformanceFlagged {

    /**
     * This flag permission is granted to tenant objects only.
     * <p>
     * This can be used in role / permission mappings to filter specific user roles for the system tenant.
     * When a user of the system tenant takes control over another tenant, this permission is kept.
     * <p>
     * Use {@link #hasPermission(String)} to check for this flag. For user specific permissions the flags
     * {@link TenantUserManager#PERMISSION_SYSTEM_TENANT_MEMBER} and
     * {@link TenantUserManager#PERMISSION_SYSTEM_ADMINISTRATOR} must be used.
     */
    String PERMISSION_SYSTEM_TENANT = "flag-system-tenant";

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
