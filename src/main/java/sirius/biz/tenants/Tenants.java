/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.web.BizController;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.UnitOfWork;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.transformers.Composable;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Helps for extract the current {@link UserAccount} and {@link Tenant}.
 * <p>
 * Also, some boilerplate methods are provided to perform some assertions.
 *
 * @param <I> the type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
public abstract class Tenants<I extends Serializable, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends Composable {

    /**
     * Names the framework which must be enabled to activate the tenant based user management.
     */
    public static final String FRAMEWORK_TENANTS = "biz.tenants";

    @Part
    protected Mixing mixing;

    @Part
    @Nullable
    private Processes processes;

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
     * @return the currently logged-in user
     */
    @Nonnull
    public U getRequiredUser() {
        return getCurrentUser().orElseThrow(this::createMissingUserException);
    }

    private HandledException createMissingUserException() {
        return Exceptions.handle()
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
        return fetchCachedTenant(UserContext.getCurrentUser().getTenantId());
    }

    /**
     * Returns the tenant of the currently logged-in user or throws an exception if no user is present.
     *
     * @return the tenant of the currently logged-in user
     */
    @Nonnull
    public T getRequiredTenant() {
        return getCurrentTenant().orElseThrow(this::createMissingTenantException);
    }

    private HandledException createMissingTenantException() {
        return Exceptions.handle()
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
     * <p>
     * If <tt>null</tt> is passed in, not checks will be performed.
     *
     * @param tenantAware {@link TenantAware} entity to be asserted
     */
    public void assertTenant(@Nullable TenantAware tenantAware) {
        if (tenantAware == null) {
            return;
        }

        assertTenant(tenantAware.getTenantAsString());
    }

    /**
     * Checks if the given id belongs to the current tenant.
     * <p>
     * If an empty string is passed in, not checks will be performed.
     *
     * @param tenantId the id to be checked
     */
    @SuppressWarnings("squid:S1612")
    @Explain("Using a method reference here leads to a BootstrapMethod error due to a JDK bug "
             + "see https://bugs.openjdk.java.net/browse/JDK-8058112 (seems to be also present in OracleJDK)")
    public void assertTenant(@Nullable String tenantId) {
        String currentTenantId = getCurrentTenant().map(tenant -> tenant.getIdAsString()).orElse(null);
        if (Strings.isFilled(tenantId) && !Strings.areEqual(tenantId, currentTenantId)) {
            throw Exceptions.createHandled().withNLSKey("Tenants.invalidTenant").handle();
        }
    }

    /**
     * Checks if the tenant aware entity belongs to the current tenant or to its parent tenant.
     *
     * @param tenantAware {@link TenantAware} entity to be asserted
     */
    @SuppressWarnings("squid:S1612")
    @Explain("Using a method reference here leads to a BootstrapMethod error due to a JDK bug "
             + "see https://bugs.openjdk.java.net/browse/JDK-8058112 (seems to be also present in OracleJDK)")
    public void assertTenantOrParentTenant(TenantAware tenantAware) {
        if (tenantAware == null) {
            return;
        }

        String currentTenantId = getCurrentTenant().map(tenant -> tenant.getIdAsString()).orElse(null);
        if (!Strings.areEqual(tenantAware.getTenantAsString(), currentTenantId)
            && !Objects.equals(tenantAware.getTenantAsString(),
                               getCurrentTenant().map(tenant -> tenant.getParent())
                                                 .filter(BaseEntityRef::isFilled)
                                                 .map(entityRef -> entityRef.getIdAsString())
                                                 .orElse(null))) {
            throw Exceptions.createHandled().withNLSKey("Tenants.invalidTenant").handle();
        }
    }

    /**
     * Applies an appropriate filter to the given query to only return entities which belong to the current tenant.
     *
     * @param query the query to extend
     * @param <E>   the type of entities processed by the query
     * @param <Q>   the type of the query which is being extended
     * @return the query with an additional constraint filtering on the current tenant
     * @throws sirius.kernel.health.HandledException if there is currently no user / tenant available
     */
    public <E extends BaseEntity<?> & TenantAware, Q extends Query<Q, E, ?>> Q forCurrentTenant(Q query) {
        return query.eq(TenantAware.TENANT, getRequiredTenant());
    }

    /**
     * Applies an appropriate filter to the given query to only return entities which belong to the current tenant
     * and the parent tenant if present.
     *
     * @param query the query to extend
     * @param <E>   the type of entities processed by the query
     * @param <Q>   the type of the query which is being extended
     * @return the query with an additional constraint filtering on the current tenant and parent tenant if present
     * @throws HandledException if there is currently no user / tenant available
     */
    public <E extends BaseEntity<?> & TenantAware, C extends Constraint, Q extends Query<Q, E, C>> Q forCurrentOrParentTenant(
            Q query) {
        if (getRequiredTenant().getParent().isFilled()) {
            return query.where(query.filters()
                                    .or(query.filters().eq(TenantAware.TENANT, getRequiredTenant()),
                                        query.filters().eq(TenantAware.TENANT, getRequiredTenant().getParent())));
        } else {
            return query.eq(TenantAware.TENANT, getRequiredTenant());
        }
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
    public Optional<T> fetchCachedTenant(String tenantId) {
        if (Strings.isEmpty(tenantId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(getTenantUserManager().fetchTenant(tenantId));
    }

    /**
     * Provides access to the tenant with the given id or throws an exception if no tenant is present.
     * <p>
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient.
     *
     * @param tenantId the id of the tenant to fetch
     * @return the tenant with the given id
     */
    public T fetchCachedRequiredTenant(String tenantId) {
        return fetchCachedTenant(tenantId).orElseThrow(this::createMissingTenantException);
    }

    /**
     * Boilerplate to quickly fetch the name of the tenant with the given id.
     *
     * @param tenantId the tenant to fetch the name for
     * @return the name of the tenant or an empty string if the tenant doesn't exist
     */
    public String fetchCachedTenantName(String tenantId) {
        return fetchCachedTenant(tenantId).map(tenant -> tenant.getTenantData().getName()).orElse("");
    }

    /**
     * Provides access to the tenant stored in the given reference.
     * <p>
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient.
     *
     * @param tenantRef the reference to read the tenant from
     * @return the tenant with the given id or an empty optional if the tenant cannot be resolved
     */
    public Optional<T> fetchCachedTenant(BaseEntityRef<I, T> tenantRef) {
        if (tenantRef.isEmpty()) {
            return Optional.empty();
        }

        return fetchCachedTenant(String.valueOf(tenantRef.getId()));
    }

    /**
     * Provides access to the tenant stored in the given reference or throws an exception if no tenant is present.
     * <p>
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient.
     *
     * @param tenantRef the reference to read the tenant from
     * @return the tenant with the given id
     */
    public T fetchCachedRequiredTenant(BaseEntityRef<I, T> tenantRef) {
        return fetchCachedTenant(tenantRef).orElseThrow(this::createMissingTenantException);
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
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient.
     *
     * @param userId the id of the user to fetch
     * @return the user account with the given id or an empty optional if the user cannot be resolved
     */
    public Optional<U> fetchCachedUserAccount(String userId) {
        if (Strings.isEmpty(userId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(getTenantUserManager().fetchAccount(Mixing.getUniqueName(getUserClass(), userId)));
    }

    /**
     * Provides access to the user account with the given id or throws an exception if no user account is present.
     * <p>
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient.
     *
     * @param userId the id of the user to fetch
     * @return the user account with the given id
     */
    public U fetchCachedRequiredUserAccount(String userId) {
        return fetchCachedUserAccount(userId).orElseThrow(this::createMissingUserException);
    }

    /**
     * Boilerplate to quickly fetch the email of the user with the given id.
     *
     * @param userId the user to fetch the email for
     * @return the email of the user or an empty string if the user doesn't exist
     */
    public String fetchCachedUserMail(String userId) {
        return fetchCachedUserAccount(userId).map(user -> user.getUserAccountData().getEmail()).orElse("");
    }

    /**
     * Boilerplate to quickly fetch the name of the user with the given id.
     * Uses {@link U#toString()}.
     *
     * @param userId the user to fetch the name for
     * @return the name of the user or an empty string if the user doesn't exist
     */
    public String fetchCachedUserName(String userId) {
        return fetchCachedUserAccount(userId).map(UserAccount::toString).orElse("");
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

        return fetchCachedUserAccount(userRef.getIdAsString());
    }

    /**
     * Provides access to the user account stored in the given reference or throws an exception if no user account is
     * present.
     * <p>
     * This utilizes the cache maintained by the {@link TenantUserManager} and is therefore quite efficient
     *
     * @param userRef the reference to read the user from
     * @return the user account with the given id
     */
    public U fetchCachedRequiredUserAccount(BaseEntityRef<I, U> userRef) {
        return fetchCachedUserAccount(userRef).orElseThrow(this::createMissingUserException);
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

    /**
     * Boilerplate to quickly fetch the name of the user account in the given reference.
     * Uses {@link U#toString()}.
     *
     * @param userRef the reference to read the user from
     * @return the name of the user or an empty string if the user doesn't exist
     */
    public String fetchCachedUserName(BaseEntityRef<I, U> userRef) {
        return fetchCachedUserAccount(userRef).map(UserAccount::toString).orElse("");
    }

    /**
     * Returns the id of the system tenant.
     * <p>
     * This is boilerplate for {@code getTenantUserManager().getSystemTenantId()}.
     *
     * @return the id of the system tenant
     */
    public String getSystemTenantId() {
        return getTenantUserManager().getSystemTenantId();
    }

    /**
     * Returns the name of the system tenant.
     * <p>
     * This is boilerplate for {@code fetchCachedTenantName(getSystemTenantId())}.
     *
     * @return the name of the system tenant
     */
    public String getSystemTenantName() {
        return fetchCachedTenantName(getSystemTenantId());
    }

    /**
     * Executes the given code as (System-)"Administrator".
     * <p>
     * Like {@link UserInfo#NOBODY} this is an artificial user which belongs to the system tenant. Note that
     * this user has no proper user id but rather only supplies a tenant id and name. This can be used e.g.
     * to create standby processes or use the {@link sirius.biz.storage.layer2.BlobStorageSpace blob storage APIs}
     * in a tenant independent manner.
     *
     * @param task the task to execute
     * @throws Exception any exception which is thrown within the task will be propagated to the outside
     * @see #asAdmin(Producer)
     * @see #runAsAdminOfTenant(String, String, UnitOfWork)
     */
    public void runAsAdmin(UnitOfWork task) throws Exception {
        asAdmin(() -> {
            task.execute();
            return null;
        });
    }

    /**
     * Runs the given <tt>task</tt> as a {@link sirius.biz.process.Process} as {@link #asAdmin(Producer) admin user}.
     *
     * @param processName the name/label of the created process
     * @param task        the task which actually uses {@link ProcessContext} to communicate with the outside world
     */
    public void runAsAdminProcess(String processName, Callback<ProcessContext> task) {
        if (processes == null) {
            throw new IllegalStateException("Cannot run an admin process, as the 'processes' framework isn't active.");
        }

        try {
            runAsAdmin(() -> {
                String processId = processes.createProcessForCurrentUser(null,
                                                                         processName,
                                                                         "fa-cogs",
                                                                         PersistencePeriod.THREE_DAYS,
                                                                         Collections.emptyMap());
                processes.execute(processId, processContext -> {
                    try {
                        processContext.markRunning();
                        task.invoke(processContext);
                    } catch (Exception exception) {
                        processContext.handle(exception);
                    }
                });
            });
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    /**
     * Executes the given code as (System-)"Administrator" and permits to return a value.
     *
     * @param task the task to execute
     * @return the value as returned by the given task
     * @throws Exception any exception which is thrown within the task will be propagated to the outside
     * @see #runAsAdmin(UnitOfWork)
     * @see #asAdminOfTenant(String, String, Producer)
     */
    public <R> R asAdmin(Producer<R> task) throws Exception {
        return asAdminOfTenant(getSystemTenantId(), getSystemTenantName(), task);
    }

    /**
     * Executes the given code as (Tenant-)"Administrator".
     * <p>
     * Like {@link UserInfo#NOBODY} this is an artificial user which belongs to the given tenant. Note that
     * this user has no proper user id but rather only supplies a tenant id and name.
     *
     * @param tenantId   the id of the tenant to execute the task for
     * @param tenantName the name of the tenant to execute the task for
     * @param task       the task to execute
     * @throws Exception any exception which is thrown within the task will be propagated to the outside
     * @see #runAsAdmin(UnitOfWork)
     * @see #asAdminOfTenant(String, String, Producer)
     */
    public void runAsAdminOfTenant(String tenantId, String tenantName, UnitOfWork task) throws Exception {
        asAdminOfTenant(tenantId, tenantName, () -> {
            task.execute();
            return null;
        });
    }

    /**
     * Executes the given code as (Tenant-)"Administrator" and permits to return a value.
     *
     * @param tenantId   the id of the tenant to execute the task for
     * @param tenantName the name of the tenant to execute the task for
     * @param task       the task to execute
     * @return the value as returned by the given task
     * @throws Exception any exception which is thrown within the task will be propagated to the outside
     * @see #runAsAdminOfTenant(String, String, UnitOfWork)
     * @see #asAdmin(Producer)
     */
    public <R> R asAdminOfTenant(String tenantId, String tenantName, Producer<R> task) throws Exception {
        UserContext userContext = UserContext.get();
        UserInfo currentUser = userContext.getUser();
        try {
            userContext.setCurrentUser(UserInfo.Builder.createSyntheticAdminUser(tenantId, tenantName).build());
            return task.create();
        } finally {
            userContext.setCurrentUser(currentUser);
        }
    }

    /**
     * Fetches all parent tenant IDs for the given tenant.
     *
     * @param tenantId the initial tenant ID
     * @return a list of all parent tenant IDs, starting with the given tenant ID
     */
    public List<String> fetchAllParentIds(String tenantId) {
        List<String> tenantIds = new ArrayList<>();
        String currentTenantId = tenantId;

        while (Strings.isFilled(currentTenantId)) {
            tenantIds.add(currentTenantId);
            currentTenantId = fetchParentTenantId(currentTenantId);
        }

        return tenantIds;
    }

    private String fetchParentTenantId(String currentTenantId) {
        return fetchCachedTenant(currentTenantId).map(T::getParent)
                                                 .map(BaseEntityRef::getIdAsString)
                                                 .filter(parentId -> !currentTenantId.equals(parentId))
                                                 .orElse(null);
    }
}
