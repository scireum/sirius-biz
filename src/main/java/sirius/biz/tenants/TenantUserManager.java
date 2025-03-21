/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import com.typesafe.config.Config;
import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.analytics.events.UserActivityEvent;
import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.model.LoginData;
import sirius.biz.protocol.AuditLog;
import sirius.biz.web.SpyUser;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mixing;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.web.http.WebContext;
import sirius.web.security.GenericUserManager;
import sirius.web.security.Permissions;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;
import sirius.web.security.UserManager;
import sirius.web.security.UserSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Provides a {@link UserManager} for {@link Tenant} and {@link UserAccount}.
 * <p>
 * The user managed can be installed by setting the <tt>manager</tt> property of the scope to <tt>tenants</tt>
 * in the system config.
 * <p>
 * This is the default user manager for the default scope in <tt>sirius-biz</tt>.
 *
 * @param <I> the type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
public abstract class TenantUserManager<I extends Serializable, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends GenericUserManager {

    /**
     * This flag permission is granted to <b>all administrators</b> (users with permission <tt>permission-manage-system</tt>)
     * of the system tenant.
     * <p>
     * The id of the system tenant can be set in the scope config. The system tenant usually is the administrative
     * company which owns / runs the system.
     */
    public static final String PERMISSION_SYSTEM_ADMINISTRATOR = "flag-system-administrator";

    /**
     * This flag permission is granted to <b>all users</b> which belong to the system tenant.
     * <p>
     * The id of the system tenant can be set in the system config. The system tenant usually is the administrative
     * company which owns / runs the system, this flag is kept when the user has taken control over another tenant,
     * but is removed if the user has taken control over another user directly.
     */
    public static final String PERMISSION_SYSTEM_TENANT_MEMBER = "flag-system-tenant-member";

    /**
     * This flag permission is granted to <b>all users</b> which originally belong to the system tenant.
     * <p>
     * The id of the system tenant can be set in the system config. The system tenant usually is the administrative
     * company which owns / runs the system.
     * Unlike {@link #PERMISSION_SYSTEM_TENANT_MEMBER}, this flag is kept always,
     * even when the user either has taken control over another tenant or user account.
     */
    public static final String PERMISSION_SYSTEM_TENANT_AFFILIATE = "flag-system-tenant-affiliate";

    /**
     * This flag permission is granted to all users which access the application from outside of
     * the configured ip range.
     */
    public static final String PERMISSION_OUT_OF_IP_RANGE = "flag-out-of-ip-range";

    /**
     * This flag indicates that the current user belongs to a tenant with child tenants.
     */
    public static final String PERMISSION_HAS_CHILDREN = "flag-has-children";

    /**
     * This flag indicates that the current user either has taken control over another tenant or user account.
     */
    public static final String PERMISSION_SPY_USER = "flag-spy-user";

    /**
     * Contains the permission required to switch the user account.
     */
    public static final String PERMISSION_SELECT_USER_ACCOUNT = "permission-select-user-account";

    /**
     * Contains the permission required to switch the tenant.
     */
    public static final String PERMISSION_SELECT_TENANT = "permission-select-tenant";

    /**
     * If a session-value named {@code UserContext.getCurrentScope().getScopeId() +
     * TenantUserManager.TENANT_SPY_ID_SUFFIX}
     * is present, the user will belong to the given tenant and not to his own one.
     * <p>
     * This is used by support and administrative tasks. Beware, that the id is not checked, so the one who installs
     * the ID has to verify that the user is allowed to switch to this tenant.
     */
    public static final String TENANT_SPY_ID_SUFFIX = "-tenant-spy-id";

    /**
     * If a session-value named {@code UserContext.getCurrentUser().getUserId() + TenantUserManager.SPY_ID_SUFFIX}
     * is present, the user with the given ID will be used, instead of the current one.
     * <p>
     * This is used by support and administrative tasks. Beware, that the id is not checked, so the one who installs the
     * ID has to verify that the user is allowed to become this user.
     */
    public static final String SPY_ID_SUFFIX = "-spy-id";

    /**
     * Contains the suffix used to store the fingerprint in the session.
     *
     * @see LoginData#FINGERPRINT
     */
    private static final String SUFFIX_FINGERPRINT = "-fingerprint";

    /**
     * Contains the prefix added to the account number which is added as artificial role.
     * <p>
     * Therefore, each user of a tenant has the following additional role (if filled):
     * {@code ROLE_PREFIX_ACCOUNT_NUMBER + tenant.getTenantData().getAccountNumber()}
     */
    public static final String ROLE_PREFIX_ACCOUNT_NUMBER = "tenant-";

    /**
     * Contains the prefix added to all performance flags when they're made visible as roles.
     * <p>
     * This simplifies checking if a user or its tenant has a certain flag active.
     */
    public static final String ROLE_PREFIX_PERFORMANCE_FLAG = "performance-flag-";

    protected final String systemTenant;
    protected final boolean acceptApiTokens;
    protected final boolean acceptHashedApiTokens;
    protected final List<String> availableLanguages;

    @Part
    @Nullable
    protected static Tenants<?, ?, ?> tenants;

    @Part
    protected static Mixing mixing;

    @Part
    protected static AuditLog auditLog;

    @Part
    protected static EventRecorder eventRecorder;

    @Parts(AdditionalRolesProvider.class)
    private static PartCollection<AdditionalRolesProvider> additionalRolesProviders;

    private static final String REMOVE_BY_TENANT_UNIQUE_NAME = "tenant-unique-name";

    protected static Cache<String, Tuple<Set<String>, String>> rolesCache =
            CacheManager.<Tuple<Set<String>, String>>createCoherentCache("tenants-roles")
                        .addRemover(REMOVE_BY_TENANT_UNIQUE_NAME,
                                    (uniqueTenantName, entry) -> Strings.areEqual(uniqueTenantName,
                                                                                  entry.getValue().getSecond()));

    protected static Cache<String, UserAccount<?, ?>> userAccountCache =
            CacheManager.createCoherentCache("tenants-users");
    protected static Cache<String, Tenant<?>> tenantsCache = CacheManager.createCoherentCache("tenants-tenants");

    protected static Cache<String, Tuple<UserSettings, String>> configCache =
            CacheManager.<Tuple<UserSettings, String>>createCoherentCache("tenants-configs")
                        .addRemover(REMOVE_BY_TENANT_UNIQUE_NAME,
                                    (uniqueTenantName, entry) -> Strings.areEqual(uniqueTenantName,
                                                                                  entry.getValue().getSecond()));

    protected TenantUserManager(ScopeInfo scope, Extension config) {
        super(scope, config);
        this.systemTenant = config.get("system-tenant").asString();
        this.acceptApiTokens = config.get("accept-api-tokens").asBoolean(false);
        this.acceptHashedApiTokens = config.get("accept-hashed-api-tokens").asBoolean(false);
        this.availableLanguages = scope.getDisplayLanguages().stream().toList();
    }

    /**
     * Flushes all caches for the given account.
     *
     * @param account the account to flush
     */
    public static void flushCacheForUserAccount(UserAccount<?, ?> account) {
        rolesCache.remove(account.getUniqueName());
        userAccountCache.remove(account.getUniqueName());
        configCache.remove(account.getUniqueName());
    }

    /**
     * Flushes all caches for the given tenant.
     *
     * @param tenant the tenant to flush
     */
    public static void flushCacheForTenant(Tenant<?> tenant) {
        tenantsCache.remove(tenant.getIdAsString());
        configCache.remove(tenant.getUniqueName());
        configCache.removeAll(REMOVE_BY_TENANT_UNIQUE_NAME, tenant.getUniqueName());
        rolesCache.removeAll(REMOVE_BY_TENANT_UNIQUE_NAME, tenant.getUniqueName());
    }

    @Override
    protected UserInfo findUserInSession(WebContext webContext) {
        UserInfo rootUser = super.findUserInSession(webContext);
        if (rootUser == null || defaultUser.equals(rootUser)) {
            return rootUser;
        }

        checkAndUpdateLastSeen(rootUser);

        String spyId = webContext.getSessionValue(scope.getScopeId() + SPY_ID_SUFFIX).asString();
        if (Strings.isFilled(spyId)) {
            UserInfo spy = becomeSpyUser(spyId, rootUser);
            if (spy != null) {
                return spy;
            }
        }

        String tenantSpyId = webContext.getSessionValue(scope.getScopeId() + TENANT_SPY_ID_SUFFIX).asString();
        if (Strings.isFilled(tenantSpyId)) {
            return createUserWithTenant(rootUser, tenantSpyId);
        }

        return rootUser;
    }

    @SuppressWarnings("unchecked")
    private void checkAndUpdateLastSeen(UserInfo userInfo) {
        U user = (U) userInfo.as(UserAccount.class);
        LocalDate userLastSeen = user.getUserAccountData().getLogin().getLastSeen();
        if (userLastSeen != null && !userLastSeen.isBefore(LocalDate.now())) {
            return;
        }

        updateLastSeen(user);
        flushCacheForUserAccount(user);
        recordUserActivityEvent(userInfo);
    }

    private void recordUserActivityEvent(UserInfo userInfo) {
        UserActivityEvent userActivityEvent = new UserActivityEvent();
        userActivityEvent.getUserData().setTenantId(userInfo.getTenantId());
        userActivityEvent.getUserData().setCustomUserId(userInfo.getUserId());
        userActivityEvent.getUserData().setScopeId(ScopeInfo.DEFAULT_SCOPE.getScopeId());
        eventRecorder.record(userActivityEvent);
    }

    /**
     * Updates the {@link LoginData#LAST_SEEN} field of the given user.
     *
     * @param user the user to update
     */
    protected abstract void updateLastSeen(U user);

    /**
     * Makes the user become/switch to another user.
     * <p>
     * See {@link UserAccountController#selectUserAccount}.
     *
     * @param spyId        the id of the user to become
     * @param rootUserInfo the original user that is becoming another user
     * @return the new user that was switched to
     */
    private UserInfo becomeSpyUser(String spyId, UserInfo rootUserInfo) {
        U spyUser = fetchAccount(spyId);
        if (spyUser == null) {
            return null;
        }
        List<String> extraRoles = new ArrayList<>();

        extraRoles.add(PERMISSION_SPY_USER);
        extraRoles.add(PERMISSION_SELECT_USER_ACCOUNT);
        if (rootUserInfo.hasPermission(PERMISSION_SYSTEM_TENANT_AFFILIATE)) {
            extraRoles.add(PERMISSION_SYSTEM_TENANT_AFFILIATE);
            if (rootUserInfo.hasPermission(PERMISSION_SYSTEM_ADMINISTRATOR)) {
                extraRoles.add(PERMISSION_SYSTEM_ADMINISTRATOR);
            }
        }

        UserInfo spyUserInfo = asUser(spyUser,
                                      extraRoles,
                                      () -> Strings.apply("%s - %s",
                                                          computeUsername(null, rootUserInfo.getUserId()),
                                                          computeTenantname(null, rootUserInfo.getTenantId())));
        spyUserInfo.attach(SpyUser.class, new SpyUser(rootUserInfo));
        return spyUserInfo;
    }

    @Override
    public UserInfo createUserWithTenant(UserInfo originalUserInfo, String tenantId) {
        if (Strings.isEmpty(tenantId) || Strings.areEqual(originalUserInfo.getTenantId(), tenantId)) {
            return originalUserInfo;
        }
        T tenant = fetchTenant(tenantId);
        if (tenant == null) {
            return originalUserInfo;
        }
        return switchTenant(originalUserInfo, tenant);
    }

    /**
     * Makes the user appear as he is from another tenant.
     * <p>
     * See {@link TenantController#selectTenant}.
     *
     * @param originalUserInfo the user to be switched
     * @param tenant           the tenant to switch to
     * @return user the new user, now belonging to the given tenant
     */
    private UserInfo switchTenant(UserInfo originalUserInfo, T tenant) {
        // Copy all relevant data into a new object (outside of the cache)...
        U modifiedUser = cloneUser(originalUserInfo);

        // And overwrite with the new tenant...
        modifiedUser.getTenant().setValue(tenant);

        Set<String> roles = computeRoles(modifiedUser,
                                         tenant,
                                         Strings.areEqual(systemTenant,
                                                          String.valueOf(originalUserInfo.getTenantId())));
        roles.add(PERMISSION_SPY_USER);
        roles.add(PERMISSION_SELECT_TENANT);

        UserInfo userInOtherTenant =
                asUserWithRoles(modifiedUser, roles, () -> computeTenantname(null, originalUserInfo.getTenantId()));
        userInOtherTenant.attach(SpyUser.class, new SpyUser(originalUserInfo));
        return userInOtherTenant;
    }

    protected U cloneUser(UserInfo originalUserInfo) {
        try {
            U currentUser = originalUserInfo.getUserObject(getUserClass());
            U modifiedUser = getUserClass().getDeclaredConstructor().newInstance();

            modifiedUser.setId(currentUser.getId());
            currentUser.getDescriptor().getProperties().forEach(property -> {
                property.setValue(modifiedUser, property.getValue(currentUser));
            });

            return modifiedUser;
        } catch (Exception exception) {
            throw Exceptions.handle(Log.APPLICATION, exception);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    protected T fetchTenant(String tenantId) {
        T tenant = (T) tenantsCache.get(tenantId);
        if (tenant != null) {
            return tenant;
        }

        tenant = loadTenant(tenantId);
        if (tenant != null) {
            tenantsCache.put(tenantId, tenant);
        }
        return tenant;
    }

    protected T loadTenant(String tenantId) {
        return mixing.getDescriptor(getTenantClass()).getMapper().find(getTenantClass(), tenantId).orElse(null);
    }

    /**
     * Determines the original tenant ID.
     *
     * @param webContext the current web request
     * @return the original tenant id of the currently active user (without spy overwrites)
     */
    public String getOriginalTenantId(WebContext webContext) {
        return webContext.getSessionValue(this.scope.getScopeId() + "-tenant-id").asString();
    }

    /**
     * Determines the original tenant ID of the current request.
     *
     * @return the original tenant id of the currently active user (without spy overwrites)
     */
    public String getOriginalTenantId() {
        return getOriginalTenantId(WebContext.getCurrent());
    }

    /**
     * Determines the original user ID.
     *
     * @param webContext the current web request
     * @return the original user id of the currently active user (without spy overwrites)
     */
    public String getOriginalUserId(WebContext webContext) {
        return webContext.getSessionValue(this.scope.getScopeId() + "-user-id").asString();
    }

    /**
     * Determines the original user ID of the current request.
     *
     * @return the original user id of the currently active user (without spy overwrites)
     */
    public String getOriginalUserId() {
        return getOriginalUserId(WebContext.getCurrent());
    }

    @Override
    public UserInfo findUserByName(@Nullable WebContext webContext, String user) {
        if (Strings.isEmpty(user)) {
            return null;
        }

        Optional<U> optionalAccount = loadAccountByName(user.toLowerCase());

        if (optionalAccount.isEmpty()) {
            return null;
        }

        U account = optionalAccount.get();
        T tenant = account.getTenant().forceFetchValue();

        userAccountCache.put(account.getUniqueName(), account);
        tenantsCache.put(tenant.getIdAsString(), tenant);
        rolesCache.remove(account.getUniqueName());
        configCache.remove(account.getUniqueName());

        if (account.getUserAccountData().getLogin().isAccountLocked()) {
            throw Exceptions.createHandled().withNLSKey("LoginData.accountIsLocked").handle();
        }

        return asUser(account, null, null);
    }

    @SuppressWarnings("unchecked")
    protected Optional<U> loadAccountByName(String user) {
        return (Optional<U>) (Object) mixing.getDescriptor(getUserClass())
                                            .getMapper()
                                            .select(getUserClass())
                                            .eq(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                             .inner(LoginData.USERNAME),
                                                user.toLowerCase())
                                            .one();
    }

    /**
     * Tries to find a {@link UserInfo} for the given unique object name of a {@link UserAccount}.
     *
     * @param accountId the unique object name of an <tt>UserAccount</tt> to resolve into a <tt>UserInfo</tt>
     * @return the <tt>UserInfo</tt> representing the given account (will utilize caches if available) or <tt>null</tt>
     * if no such user exists
     */
    @Nullable
    @Override
    public UserInfo findUserByUserId(String accountId) {
        U account = fetchAccount(accountId);
        if (account == null) {
            return null;
        }
        return asUser(account, null, null);
    }

    @Nonnull
    @Override
    public UserInfo bindToRequest(@Nonnull WebContext webContext) {
        UserInfo info = super.bindToRequest(webContext);
        if (info.isLoggedIn()) {
            // We only need to verify the IP range for users being logged in...
            return verifyIpRange(webContext, info);
        } else {
            return info;
        }
    }

    /**
     * Checks if the current user violates the configured ip range constraints.
     * <p>
     * Will remove permissions if the users ip address does not match the configured
     * ip range.
     * <p>
     * This is a security feature to prevent unwanted access to certain features from anywhere but a valid ip range.
     *
     * @param webContext the current request
     * @param info       the current user
     * @return the current user but possibly with less permissions
     */
    private UserInfo verifyIpRange(WebContext webContext, UserInfo info) {
        String actualUser = webContext.getSessionValue(scope.getScopeId() + "-user-id").asString();

        U account = fetchAccount(actualUser);

        if (account == null) {
            return defaultUser;
        }

        T tenant = fetchTenant(account.getTenant().getIdAsString());

        if (tenant != null && !tenant.getTenantData().matchesIPRange(webContext)) {
            return createUserWithLimitedRoles(info, tenant.getTenantData().getRolesToKeepAsSet());
        }

        return info;
    }

    /**
     * Removes all roles from the given user other than the roles to keep.
     * <p>
     * Will only allow the user to keep roles defined in rolesToKeep. Will not give the user any other role.
     *
     * @param info        the user info to modify
     * @param rolesToKeep the roles not to remove
     * @return the modified user info
     */
    private UserInfo createUserWithLimitedRoles(UserInfo info, Set<String> rolesToKeep) {
        Set<String> oldRoles = info.getPermissions();

        Set<String> roles = new HashSet<>();
        roles.add(UserInfo.PERMISSION_LOGGED_IN);
        roles.add(PERMISSION_OUT_OF_IP_RANGE);

        for (String role : rolesToKeep) {
            if (oldRoles.contains(role)) {
                roles.add(role);
            }
        }

        return UserInfo.Builder.withUser(info).withPermissions(roles).build();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    protected U fetchAccount(@Nonnull String accountId) {
        U account = (U) userAccountCache.get(accountId);
        if (account == null) {
            account = loadAccount(accountId);
            if (account == null) {
                return null;
            }
            userAccountCache.put(account.getUniqueName(), account);
            T tenant = account.getTenant().forceFetchValue();
            tenantsCache.put(tenant.getIdAsString(), tenant);
            rolesCache.remove(account.getUniqueName());
            configCache.remove(account.getUniqueName());

            return account;
        }

        T tenant = fetchTenant(String.valueOf(account.getTenant().getId()));
        if (tenant == null) {
            return null;
        }
        account.getTenant().setValue(tenant);

        return account;
    }

    @SuppressWarnings("unchecked")
    protected U loadAccount(@Nonnull String accountId) {
        return (U) mixing.getDescriptor(getUserClass()).getMapper().resolve(accountId).orElse(null);
    }

    protected UserInfo asUser(U account, List<String> extraRoles, @Nullable Supplier<String> appendixSupplier) {
        Set<String> roles = computeRoles(null, account.getUniqueName());
        if (extraRoles != null) {
            // Make a copy so that we do not modify the cached set...
            roles = new TreeSet<>(roles);
            roles.addAll(extraRoles);
        }
        return asUserWithRoles(account, roles, appendixSupplier);
    }

    private UserInfo asUserWithRoles(U account, Set<String> roles, @Nullable Supplier<String> appendixSupplier) {
        return UserInfo.Builder.createUser(account.getUniqueName())
                               .withUsername(account.getUserAccountData().getLogin().getUsername())
                               .withTenantId(String.valueOf(account.getTenant().getId()))
                               .withTenantName(fetchTenant(account.getTenant().getIdAsString()).getTenantData()
                                                                                               .getName())
                               .withLanguage(computeLanguage(null, account.getUniqueName()))
                               .withPermissions(roles)
                               .withSettingsSupplier(user -> getUserSettings(getScopeSettings(), user))
                               .withUserSupplier(ignored -> account)
                               .withNameAppendixSupplier(appendixSupplier)
                               .withSubScopeCheck(this::checkSubScope)
                               .build();
    }

    @Override
    protected boolean checkSubScope(UserInfo userInfo, String scope) {
        if (!userInfo.isLoggedIn()) {
            return true;
        }

        UserAccountData userAccountData = userInfo.getUserObject(UserAccount.class).getUserAccountData();
        return userAccountData.getSubScopes().isEmpty() || userAccountData.getSubScopes().contains(scope);
    }

    @Override
    public UserInfo findUserByCredentials(@Nullable WebContext webContext, String user, String password) {
        if (Strings.isEmpty(password)) {
            return null;
        }

        UserInfo result = findUserByName(webContext, user);
        if (result == null) {
            auditLog.negative("AuditLog.lockedOrNonexistentUserTriedLogin").forUser(null, user).log();
            return null;
        }

        U account = result.getUserObject(getUserClass());
        T tenant = fetchTenant(account.getTenant().getIdAsString());
        if (account.getUserAccountData().isExternalLoginRequired() && !isWithinInterval(account.getUserAccountData()
                                                                                               .getLogin()
                                                                                               .getLastExternalLogin(),
                                                                                        tenant.getTenantData()
                                                                                              .getExternalLoginIntervalDays())) {
            completeAuditLogForUser(auditLog.negative("AuditLog.externalLoginRequired"), account);
            throw Exceptions.createHandled().withNLSKey("UserAccount.externalLoginMustBePerformed").handle();
        }

        LoginData loginData = account.getUserAccountData().getLogin();
        LoginData.PasswordVerificationResult pwResult = loginData.checkPassword(user, password);
        if (pwResult != LoginData.PasswordVerificationResult.INVALID) {
            completeAuditLogForUser(auditLog.neutral("AuditLog.passwordLogin"), account);

            if (pwResult == LoginData.PasswordVerificationResult.VALID_NEEDS_RE_HASH) {
                ValueHolder<UserInfo> rehashingResult = new ValueHolder<>(null);
                UserContext.get().runAs(result, () -> rehashingResult.set(updatePasswordHashing(result, password)));

                return rehashingResult.get();
            }

            recordLogin(result, false);
            return result;
        }

        if (checkApiToken(loginData, password)) {
            completeAuditLogForUser(auditLog.neutral("AuditLog.apiTokenLogin"), account);
            recordLogin(result, false);
            return result;
        }

        auditLog.negative("AuditLog.loginRejected")
                .forUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                .forTenant(tenant.getIdAsString(), tenant.getTenantData().getName())
                .log();

        return null;
    }

    protected boolean checkApiToken(LoginData loginData, String givenApiToken) {
        if (Strings.isEmpty(loginData.getApiToken())) {
            return false;
        }

        if (acceptApiTokens && Strings.areEqual(givenApiToken, loginData.getApiToken())) {
            return true;
        }

        if (acceptHashedApiTokens) {
            long currentTimestampInDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
            // Timestamps of tomorrow and yesterday should be valid too, to be more graceful with nightly scripts utilizing
            // the apiToken. If midnight passes while execution, the hashed apiToken would be suddenly invalid.
            for (int i = -1; i <= 1; i++) {
                long timestampToCheck = currentTimestampInDays + i;
                if (Strings.areEqual(getHashedApiToken(loginData.getApiToken(), timestampToCheck), givenApiToken)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Creates a md5 hash, using the given apiToken and the timestampInDays.
     *
     * @param apiToken        the apiToken
     * @param timestampInDays the timestampInDays
     * @return the md5 hash of the apiToken and timestampInDays
     */
    protected String getHashedApiToken(String apiToken, long timestampInDays) {
        return Hasher.md5().hash(apiToken).hash(String.valueOf(timestampInDays)).toHexString();
    }

    protected void completeAuditLogForUser(AuditLog.AuditLogBuilder builder, U account) {
        builder.causedByUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
               .forUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
               .forTenant(String.valueOf(account.getTenant().getId()),
                          fetchTenant(account.getTenant().getIdAsString()).getTenantData().getName())
               .log();
    }

    private UserInfo updatePasswordHashing(UserInfo info, String password) {
        try {
            U account = info.getUserObject(getUserClass());
            U freshAccount = account.getDescriptor().getMapper().tryRefresh(account);
            freshAccount.getUserAccountData().getLogin().setCleartextPassword(password);
            freshAccount.getTrace().setSilent(true);
            freshAccount.getDescriptor().getMapper().update(freshAccount);
            completeAuditLogForUser(auditLog.neutral("AuditLog.passwordReHashed"), account);
            return UserInfo.Builder.withUser(info)
                                   .withPermissions(info.getPermissions())
                                   .withUserSupplier(u -> freshAccount)
                                   .build();
        } catch (Exception exception) {
            Exceptions.handle(Log.APPLICATION, exception);
            return info;
        }
    }

    @SuppressWarnings("unchecked")
    protected Class<T> getTenantClass() {
        return (Class<T>) tenants.getTenantClass();
    }

    @SuppressWarnings("unchecked")
    protected Class<U> getUserClass() {
        return (Class<U>) tenants.getUserClass();
    }

    /**
     * Checks if the given password of the given {@link UserAccount}  is correct.
     *
     * @param userAccount the user account to validate the password for
     * @param password    the password to validate
     * @return <tt>true</tt> if the password is valid, <tt>false</tt> otherwise
     */
    public boolean checkPassword(U userAccount, String password) {
        return userAccount.getUserAccountData()
                          .getLogin()
                          .checkPassword(userAccount.getUserAccountData().getLogin().getUsername(), password)
               != LoginData.PasswordVerificationResult.INVALID;
    }

    @Override
    protected void recordUserLogin(WebContext webContext, UserInfo userInfo) {
        // Ignored, as findUserByCredentials already records all logins.
    }

    /**
     * Handles an external login (e.g. via SAML).
     *
     * @param webContext the current request
     * @param userInfo   the user which logged in
     */
    public void onExternalLogin(WebContext webContext, UserInfo userInfo) {
        updateLoginCookie(webContext, userInfo, true);
        recordLogin(userInfo, true);
    }

    protected abstract void recordLogin(UserInfo userInfo, boolean external);

    @Override
    protected U getUserObject(UserInfo userInfo) {
        return fetchAccount(userInfo.getUserId());
    }

    @Override
    protected UserSettings getUserSettings(UserSettings scopeSettings, UserInfo userInfo) {
        U user = userInfo.getUserObject(getUserClass());
        Config userAccountConfig = user.getUserAccountData().getPermissions().getConfig();
        Config tenantConfig = fetchTenant(user.getTenant().getIdAsString()).getTenantData().getConfig();

        if (userAccountConfig == null) {
            if (tenantConfig == null) {
                return scopeSettings;
            }

            return configCache.get(user.getTenant().getUniqueObjectName(), ignored -> {
                Config config = scopeSettings.getConfig();
                config = tenantConfig.withFallback(config);
                return Tuple.create(new UserSettings(config, false), user.getTenant().getUniqueObjectName());
            }).getFirst();
        }

        return configCache.get(user.getUniqueName(), ignored -> {
            Config config = scopeSettings.getConfig();

            if (tenantConfig != null) {
                config = tenantConfig.withFallback(config);
            }

            config = userAccountConfig.withFallback(config);
            return Tuple.create(new UserSettings(config, false), user.getTenant().getUniqueObjectName());
        }).getFirst();
    }

    @Override
    public void updateLoginCookie(WebContext webContext, UserInfo userInfo, boolean keepLogin) {
        super.updateLoginCookie(webContext, userInfo, keepLogin);
        installFingerprintInSession(webContext,
                                    userInfo.getUserObject(UserAccount.class)
                                            .getUserAccountData()
                                            .getLogin()
                                            .getFingerprint());
    }

    /**
     * Installs the given fingerprint into the session of the given request.
     * <p>
     * This is made externally available so that the {@link ProfileController} can instantly
     * update the fingerprint after the user changed its password. Otherwise, an
     * immediate logout would happen which would be kind of a strange user experience.
     *
     * @param webContext  the request used to update the session cookie
     * @param fingerprint the new fingerprint to use
     */
    public void installFingerprintInSession(WebContext webContext, String fingerprint) {
        webContext.setSessionValue(scope.getScopeId() + SUFFIX_FINGERPRINT, fingerprint);
    }

    @Override
    @SuppressWarnings({"squid:S1126", "RedundantIfStatement"})
    @Explain("Using explicit abort conditions, and a final true makes all checks obvious")
    protected boolean isUserStillValid(String userId, WebContext webContext) {
        U user = fetchAccount(userId);

        if (user == null) {
            return false;
        }

        LoginData loginData = user.getUserAccountData().getLogin();
        TenantData tenantData = fetchTenant(user.getTenant().getIdAsString()).getTenantData();

        if (loginData.isAccountLocked()) {
            return false;
        }

        String fingerprintInSession = webContext.getSessionValue(scope.getScopeId() + SUFFIX_FINGERPRINT).asString();
        if (Strings.isFilled(loginData.getFingerprint()) && !Strings.areEqual(loginData.getFingerprint(),
                                                                              fingerprintInSession)) {
            return false;
        }

        if (!isWithinInterval(loginData.getLastLogin(), tenantData.getLoginIntervalDays())) {
            return false;
        }

        if (user.getUserAccountData().isExternalLoginRequired() && !isWithinInterval(loginData.getLastExternalLogin(),
                                                                                     tenantData.getExternalLoginIntervalDays())) {
            return false;
        }

        return true;
    }

    private boolean isWithinInterval(LocalDateTime dateTime, Integer requiredInterval) {
        if (requiredInterval == null) {
            return true;
        }

        if (dateTime == null) {
            return false;
        }

        long actualInterval = Duration.between(dateTime, LocalDateTime.now()).toDays();
        return actualInterval < requiredInterval;
    }

    private Set<String> computeRoles(U user, T tenant, boolean isSystemTenant) {
        Set<String> roles = new TreeSet<>();
        roles.add(UserInfo.PERMISSION_LOGGED_IN);
        roles.addAll(user.getUserAccountData().getPermissions().getPermissions().data());
        roles.addAll(tenant.getTenantData().getPackageData().computeCombinedPermissions());

        if (isSystemTenant) {
            roles.add(Tenant.PERMISSION_SYSTEM_TENANT);
        }
        if (Strings.isFilled(tenant.getTenantData().getAccountNumber())) {
            roles.add(ROLE_PREFIX_ACCOUNT_NUMBER + tenant.getTenantData().getAccountNumber());
        }

        for (AdditionalRolesProvider rolesProvider : additionalRolesProviders) {
            rolesProvider.addAdditionalRoles(user, roles::add);
        }

        if (tenant.getMapper().select(tenant.getClass()).eq(Tenant.PARENT, tenant.getIdAsString()).exists()) {
            roles.add(PERMISSION_HAS_CHILDREN);
        }

        tenant.getPerformanceData()
              .activeFlags()
              .map(PerformanceFlag::getName)
              .map(flag -> ROLE_PREFIX_PERFORMANCE_FLAG + flag)
              .forEach(roles::add);

        Set<String> excludedPermissions =
                new HashSet<>(tenant.getTenantData().getPackageData().getRevokedPermissions().data());
        Set<String> transformedRoles = transformRoles(roles, excludedPermissions);
        excludedPermissions.forEach(transformedRoles::remove);

        return transformedRoles;
    }

    /**
     * Extends the list of permissions for the given tenant with what each available {@link AdditionalRolesProvider}
     * has to offer.
     *
     * @param tenant              the tenant to compute additional roles for
     * @param readOnlyPermissions the permissions which have been fetched so far.
     * @return the effective set of permissions for this tenant
     */
    public static Set<String> computeEffectiveTenantPermissions(Tenant<?> tenant, Set<String> readOnlyPermissions) {
        Collection<AdditionalRolesProvider> parts = additionalRolesProviders.getParts();
        if (parts.isEmpty()) {
            return readOnlyPermissions;
        }

        Set<String> result = new TreeSet<>(readOnlyPermissions);
        for (AdditionalRolesProvider rolesProvider : additionalRolesProviders) {
            rolesProvider.addAdditionalTenantRoles(tenant, result::add);
        }

        // also apply profiles and revokes to additional roles
        Set<String> excludedPermissions =
                new HashSet<>(tenant.getTenantData().getPackageData().getRevokedPermissions().data());
        Permissions.applyProfiles(result, excludedPermissions);
        excludedPermissions.forEach(result::remove);

        return result;
    }

    @Override
    protected Set<String> computeRoles(WebContext webContext, String accountUniqueName) {
        Tuple<Set<String>, String> cachedRoles = rolesCache.get(accountUniqueName);
        if (cachedRoles != null) {
            return cachedRoles.getFirst();
        }

        U user = fetchAccount(accountUniqueName);
        if (user == null) {
            rolesCache.put(accountUniqueName, Tuple.create(Collections.emptySet(), null));
            return Collections.emptySet();
        }

        Set<String> roles = computeRoles(user,
                                         fetchTenant(user.getTenant().getIdAsString()),
                                         Strings.areEqual(systemTenant, String.valueOf(user.getTenant().getId())));

        rolesCache.put(accountUniqueName, Tuple.create(roles, user.getTenant().getUniqueObjectName()));
        return roles;
    }

    @Nonnull
    @Override
    protected String computeUsername(@Nullable WebContext webContext, String userId) {
        U account = fetchAccount(userId);
        if (account == null) {
            return "(unknown)";
        } else {
            return account.getUserAccountData().getLogin().getUsername();
        }
    }

    @Nonnull
    @Override
    protected String computeTenantname(@Nullable WebContext webContext, String tenantId) {
        T tenant = fetchTenant(tenantId);
        if (tenant == null) {
            return "(unknown)";
        } else {
            return tenant.getTenantData().getName();
        }
    }

    /**
     * Returns the list of supported languages as ISO codes.
     *
     * @return the list of supported languages
     */
    public List<String> getAvailableLanguageCodes() {
        return availableLanguages;
    }

    /**
     * Returns a list of supported languages and their translated name.
     *
     * @return a list of tuples containing the ISO code and the translated name
     */
    public List<Tuple<String, String>> getAvailableLanguages() {
        return availableLanguages.stream().map(code -> Tuple.create(code, NLS.get("Language." + code))).toList();
    }

    @Override
    public void logout(@Nonnull WebContext webContext) {
        super.logout(webContext);
        webContext.setSessionValue(scope.getScopeId() + SPY_ID_SUFFIX, null);
        webContext.setSessionValue(scope.getScopeId() + TENANT_SPY_ID_SUFFIX, null);
    }

    /**
     * Returns the id of the system tenant.
     * <p>
     * Note that this method should only be used by the framework itself. Otherwise, use
     * {@link UserInfo#hasPermission(String)} or {@link Tenant#hasPermission(String)} and
     * {@link #PERMISSION_SYSTEM_ADMINISTRATOR} or {@link #PERMISSION_SYSTEM_TENANT_MEMBER}.
     *
     * @return the id of the system tenant
     */
    public String getSystemTenantId() {
        return systemTenant;
    }

    @Nonnull
    @Override
    protected String computeLanguage(WebContext webContext, String userId) {
        U userAccount = fetchAccount(userId);
        if (userAccount == null) {
            return NLS.getDefaultLanguage();
        }
        return Strings.firstFilled(userAccount.getUserAccountData().getLanguage().getValue(),
                                   fetchTenant(userAccount.getTenant().getIdAsString()).getTenantData().getLanguage().getValue(),
                                   NLS.getDefaultLanguage());
    }
}
