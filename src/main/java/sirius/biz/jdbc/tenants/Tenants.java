/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.tenants;

import sirius.biz.web.BizController;
import sirius.biz.web.TenantAware;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

/**
 * Helps for extract the current {@link UserAccount} and {@link Tenant}.
 * <p>
 * Also some boiler plate methods are provided to perform some assertions.
 */
@Framework("biz.tenants")
@Register(classes = Tenants.class)
public class Tenants {

    @Part
    private OMA oma;

    private Cache<Long, Boolean> tenantsWithChildren = CacheManager.createCache("tenants-children");

    /**
     * Returns the current user as {@link UserAccount} which is logged in.
     *
     * @return the current user wrapped as {@link Optional} or an empty optional, if no user is logged in.
     */
    @Nonnull
    public Optional<UserAccount> getCurrentUser() {
        UserInfo user = UserContext.getCurrentUser();
        if (user.isLoggedIn()) {
            return Optional.ofNullable(user.getUserObject(UserAccount.class));
        }

        return Optional.empty();
    }

    /**
     * Returns the current user or throws an exception if no user is currently available.
     *
     * @return the currently logged in user
     */
    @Nonnull
    public UserAccount getRequiredUser() {
        Optional<UserAccount> ua = getCurrentUser();
        if (ua.isPresent()) {
            return ua.get();
        }
        throw Exceptions.handle()
                        .to(BizController.LOG)
                        .withSystemErrorMessage("A user of type UserAccount was expected but not present!")
                        .handle();
    }

    /**
     * Determines if there is currently a user logged in.
     *
     * @return <tt>true</tt> if a user is present, <tt>false</tt> otherwise
     */
    public boolean hasUser() {
        return getCurrentUser().isPresent();
    }

    /**
     * Returns the {@link Tenant} of the current user.
     *
     * @return the tenant of the current user wrapped as {@link Optional} or an empty optional, if no user is logged in.
     */
    @Nonnull
    public Optional<Tenant> getCurrentTenant() {
        return getCurrentUser().flatMap(u -> Optional.ofNullable(u.getTenant().getValue()));
    }

    /**
     * Returns the tenant of the currently logged in user or throws an exception if no user is present.
     *
     * @return the tenant of the currently logged in user
     */
    @Nonnull
    public Tenant getRequiredTenant() {
        Optional<Tenant> t = getCurrentTenant();
        if (t.isPresent()) {
            return t.get();
        }
        throw Exceptions.handle()
                        .to(BizController.LOG)
                        .withSystemErrorMessage("A tenant of type Tenant was expected but not present!")
                        .handle();
    }

    /**
     * Determines if there is a user logged in which has a tenant.
     *
     * @return <tt>true</tt> if there is a user with a known tenant currently logged in
     */
    public boolean hasTenant() {
        return getCurrentTenant().isPresent();
    }

    /**
     * Determines if the tenant with the given ID has child tenants.
     * <p>
     * This call utilizes a cache, therefore a lookup if quite fast and cheap.
     *
     * @param tenantId the id of the tenant to check if there are children
     * @return <tt>true</tt> if the given tenant has children, <tt>false</tt> if there are no children, or if the tenant
     * id is unknown.
     */
    public boolean hasChildTenants(long tenantId) {
        return tenantsWithChildren.get(tenantId, id -> oma.select(Tenant.class).eq(Tenant.PARENT, tenantId).exists());
    }

    /**
     * Flushes the cache which determines if a tenant has children or not.
     */
    protected void flushTenantChildrenCache() {
        tenantsWithChildren.clear();
    }

    /**
     * Checks if the tenant aware entity belongs to the current tenant or to its parent tenant.
     *
     * @param tenantAware {@link TenantAware} entity to be asserted
     */
    public void assertTenantOrParentTenant(TenantAware tenantAware) {
        if (tenantAware == null) {
            return;
        }

        if (!Objects.equals(tenantAware.getTenantAsString(), getCurrentTenant().map(Tenant::getIdAsString).orElse(null))
            && !Objects.equals(tenantAware.getTenantAsString(),
                               getCurrentTenant().map(Tenant::getParent)
                                                 .map(SQLEntityRef::getIdAsString)
                                                 .orElse(null))) {
            throw Exceptions.createHandled().withNLSKey("BizController.invalidTenant").handle();
        }
    }

    public <E extends SQLTenantAware> SmartQuery<E> forCurrentTenant(SmartQuery<E> qry) {
        return qry.eq(SQLTenantAware.TENANT, getRequiredTenant());
    }
}
