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
import sirius.kernel.settings.Settings;
import sirius.pasta.noodle.sandbox.NoodleSandbox;
import sirius.web.security.UserContext;

import java.io.Serializable;
import java.util.Set;

/**
 * Provides the database independent interface for describing a tenant which uses the system.
 * <p>
 * Note that all fields are represented via {@link TenantData}.
 *
 * @param <I> the type used to represent database IDs
 */
@SuppressWarnings("squid:S1214")
@Explain("We rather keep the constants here, as this emulates the behaviour and layout of a real entity.")
public interface Tenant<I extends Serializable>
        extends Entity, Transformable, Traced, Journaled, RateLimitedEntity, PerformanceFlagged {

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
     * @return the reference to look up the parent tenant
     */
    BaseEntityRef<I, ? extends Tenant<I>> getParent();

    /**
     * Provides access to the effective tenant data.
     *
     * @return the tenant data composite which stores all values in a database independent manner.
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
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

    /**
     * Returns the set of all permissions which are effectively enabled for this tenant.
     *
     * @return the set of all permissions effectively enabled for this tenant
     */
    Set<String> getPermissions();

    /**
     * Returns the compiled and parsed settings of this tenant.
     * <p>
     * Normally, we obtain the settings via {@link UserContext#getSettings()} but sometimes we only or directly need
     * the settings of a tenant instead of those of a user + tenant.
     *
     * @return the settings of this tenant
     */
    Settings getSettings();
}
