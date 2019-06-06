/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.web.BizController;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

/**
 * Helps for extract the current {@link UserAccount} and {@link Tenant}.
 * <p>
 * Also some boiler plate methods are provided to perform some assertions.
 *
 * @param <I> the type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
public abstract class Tenants<I, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>> {

    /**
     * Names the framework which must be enabled to activate the tenant based user management.
     */
    public static final String FRAMEWORK_TENANTS = "biz.tenants";

    @Part
    protected Mixing mixing;

    protected Cache<String, Boolean> tenantsWithChildren = CacheManager.createCoherentCache("tenants-children");

    /**
     * Returns the current user as {@link UserAccount} which is logged in.
     *
     * @return the current user wrapped as {@link Optional} or an empty optional, if no user is logged in.
     */
    @Nonnull
    public Optional<U> getCurrentUser() {
        UserInfo user = UserContext.getCurrentUser();
        if (user.isLoggedIn()) {
            return Optional.ofNullable(user.getUserObject(getUserClass()));
        }

        return Optional.empty();
    }

    /**
     * Returns the effective entity class used to represent tenants.
     *
     * @return the effective implementation of {@link Tenant}
     */
    public abstract Class<T> getTenantClass();

    /**
     * Returns the effective entity class used to represent user accounts.
     *
     * @return the effective implementation of {@link UserAccount}
     */
    public abstract Class<U> getUserClass();

    /**
     * Returns the current user or throws an exception if no user is currently available.
     *
     * @return the currently logged in user
     */
    @Nonnull
    public U getRequiredUser() {
        Optional<U> ua = getCurrentUser();
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
    public Optional<T> getCurrentTenant() {
        return getCurrentUser().flatMap(u -> Optional.ofNullable(u.getTenant().getValue()));
    }

    /**
     * Returns the tenant of the currently logged in user or throws an exception if no user is present.
     *
     * @return the tenant of the currently logged in user
     */
    @Nonnull
    public T getRequiredTenant() {
        Optional<T> t = getCurrentTenant();
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
    public boolean hasChildTenants(I tenantId) {
        return tenantsWithChildren.get(String.valueOf(tenantId), id -> checkIfHasChildTenants(tenantId));
    }

    protected abstract boolean checkIfHasChildTenants(I tenantId);

    /**
     * Flushes the cache which determines if a tenant has children or not.
     */
    protected void flushTenantChildrenCache() {
        tenantsWithChildren.clear();
    }

    /**
     * Checks if the tenant aware entity belongs to the current tenant.
     *
     * @param tenantAware {@link TenantAware} entity to be asserted
     */
    @SuppressWarnings("squid:S1612")
    @Explain(
            "Calling a method reference with generics causes a known bug in JDK. See JDK-8191655 and https://stackoverflow.com/a/47471284/9758089")
    public void assertTenant(TenantAware tenantAware) {
        if (tenantAware == null) {
            return;
        }

        if (!Objects.equals(tenantAware.getTenantAsString(),
                            getCurrentTenant().map(tenant -> tenant.getIdAsString()).orElse(null))) {
            throw Exceptions.createHandled().withNLSKey("BizController.invalidTenant").handle();
        }
    }

    /**
     * Checks if the tenant aware entity belongs to the current tenant or to its parent tenant.
     *
     * @param tenantAware {@link TenantAware} entity to be asserted
     */
    @SuppressWarnings("squid:S1612")
    @Explain(
            "Calling a method reference with generics causes a known bug in JDK. See JDK-8191655 and https://stackoverflow.com/a/47471284/9758089")
    public void assertTenantOrParentTenant(TenantAware tenantAware) {
        if (tenantAware == null) {
            return;
        }

        if (!Objects.equals(tenantAware.getTenantAsString(),
                            getCurrentTenant().map(tenant -> tenant.getIdAsString()).orElse(null)) && !Objects.equals(
                tenantAware.getTenantAsString(),
                getCurrentTenant().map(tenant -> tenant.getParent())
                                  .filter(BaseEntityRef::isFilled)
                                  .map(BaseEntityRef::getId)
                                  .map(String::valueOf)
                                  .orElse(null))) {
            throw Exceptions.createHandled().withNLSKey("BizController.invalidTenant").handle();
        }
    }

    /**
     * Applies an appropriate filter to the given query to only return entities which belong to the current tenant.
     *
     * @param qry the query to extent
     * @param <E> the type of entities processed by the query
     * @param <Q> the type of the query which is being extended
     * @return the query with an additional constraint filtering on the current tenant
     * @throws sirius.kernel.health.HandledException if there is currently no user / tenant available
     */
    public <E extends BaseEntity<?> & TenantAware, Q extends Query<Q, E, ?>> Q forCurrentTenant(Q qry) {
        return qry.eq(TenantAware.TENANT, getRequiredTenant());
    }

    /**
     * Provides access to the tenant user manager by assuming it is installed in the DEFAULT_SCOPE.
     *
     * @return the tenant user manager used by the default scope
     */
    @SuppressWarnings("unchecked")
    public TenantUserManager<I, T, U> getTenantUserManager() {
        return (TenantUserManager<I, T, U>) ScopeInfo.DEFAULT_SCOPE.getUserManager();
    }

    /**
     * Provides access to the tenant with the given id
     * <p>
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient
     *
     * @param tenantId the id of the tenant to fetch
     * @return the tenant with the given id or an empty optional if the tenant cannot be resolved
     */
    public Optional<T> fetchCachedTenant(I tenantId) {
        if (Strings.isEmpty(tenantId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(getTenantUserManager().fetchTenant(String.valueOf(tenantId)));
    }

    /**
     * Boilerplate to quickly fetch the name of the tenant with the given id.
     *
     * @param tenantId the tenant to fetch the name for
     * @return the name of the tenant or an empty string if the tenant doesn't exist
     */
    public String fetchCachedTenantName(I tenantId) {
        return fetchCachedTenant(tenantId).map(tenant -> tenant.getTenantData().getName()).orElse("");
    }

    /**
     * Provides access to the tenant stored in the given reference.
     * <p>
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient
     *
     * @param tenantRef the reference to read the tenant from
     * @return the tenant with the given id or an empty optional if the tenant cannot be resolved
     */
    public Optional<T> fetchCachedTenant(BaseEntityRef<I, T> tenantRef) {
        if (tenantRef.isEmpty()) {
            return Optional.empty();
        }

        if (tenantRef.isValueLoaded()) {
            return Optional.of(tenantRef.getValue());
        }

        return fetchCachedTenant(tenantRef.getId());
    }

    /**
     * Boilerplate to quickly fetch the name of the tenant in the given reference.
     *
     * @param tenantRef the reference to read the tenant from
     * @return the name of the tenant or an empty string if the tenant doesn't exist
     */
    public String fetchCachedTenantName(BaseEntityRef<I, T> tenantRef) {
        return fetchCachedTenant(tenantRef).map(tenant -> tenant.getTenantData().getName()).orElse("");
    }

    /**
     * Provides access to the user account with the given id
     * <p>
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient
     *
     * @param userId the id of the user to fetch
     * @return the user account with the given id or an empty optional if the user cannot be resolved
     */
    public Optional<U> fetchCachedUserAccount(I userId) {
        if (Strings.isEmpty(userId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(getTenantUserManager().fetchAccount(String.valueOf(userId)));
    }

    /**
     * Boilerplate to quickly fetch the email of the user with the given id.
     *
     * @param userId the user to fetch the email for
     * @return the email of the user or an empty string if the user doesn't exist
     */
    public String fetchCachedUserMail(I userId) {
        return fetchCachedUserAccount(userId).map(user -> user.getUserAccountData().getEmail()).orElse("");
    }

    /**
     * Provides access to the user account stored in the given reference.
     * <p>
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient
     *
     * @param userRef the reference to read the user from
     * @return the user account with the given id or an empty optional if the user cannot be resolved
     */
    public Optional<U> fetchCachedUserAccount(BaseEntityRef<I, U> userRef) {
        if (userRef.isEmpty()) {
            return Optional.empty();
        }

        if (userRef.isValueLoaded()) {
            return Optional.of(userRef.getValue());
        }

        return fetchCachedUserAccount(userRef.getId());
    }

    /**
     * Boilerplate to quickly fetch the email of the user account in the given reference.
     *
     * @param userRef the reference to read the user from
     * @return the email of the user or an empty string if the user doesn't exist
     */
    public String fetchCachedUserMail(BaseEntityRef<I, U> userRef) {
        return fetchCachedUserAccount(userRef).map(user -> user.getUserAccountData().getEmail()).orElse("");
    }
}
