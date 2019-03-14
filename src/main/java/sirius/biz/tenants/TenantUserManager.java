/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import sirius.biz.model.LoginData;
import sirius.biz.protocol.AuditLog;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mixing;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.web.http.WebContext;
import sirius.web.security.GenericUserManager;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserInfo;
import sirius.web.security.UserManager;
import sirius.web.security.UserSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
public abstract class TenantUserManager<I, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends GenericUserManager {

    /**
     * This flag permission is granted to all users which belong to the system tenant.
     * <p>
     * The id of the system tenant can be set in the scope config. The system tenant usually is the administrative
     * company which owns / runs the system.
     */
    public static final String PERMISSION_SYSTEM_TENANT = "flag-system-tenant";

    /**
     * This flag permission is granted to all users which access the application from outside of
     * the configured ip range.
     */
    public static final String PERMISSION_OUT_OF_IP_RANGE = "flag-out-of-ip-range";

    /**
     * Contains the permission required to manage the system.
     * <p>
     * If this permission is granted for user accounts that belong to the system tenant, the PERMISSION_SYSTEM_TENANT
     * flag is added to the users roles
     */
    public static final String PERMISSION_MANAGE_SYSTEM = "permission-manage-system";

    /**
     * This flag indicates that the current user either has taken control over another tenant or uses account.
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

    protected final String systemTenant;
    protected final boolean acceptApiTokens;

    @Part
    protected static Tenants<?, ?, ?> tenants;

    @Part
    protected static Mixing mixing;

    @Part
    protected static AuditLog auditLog;

    protected static Cache<String, Set<String>> rolesCache = CacheManager.createCoherentCache("tenants-roles");
    protected static Cache<String, UserAccount<?, ?>> userAccountCache =
            CacheManager.createCoherentCache("tenants-users");
    protected static Cache<String, Tenant<?>> tenantsCache = CacheManager.createCoherentCache("tenants-tenants");

    protected static Cache<String, Tuple<UserSettings, String>> configCache =
            CacheManager.createCoherentCache("tenants-configs");

    protected TenantUserManager(ScopeInfo scope, Extension config) {
        super(scope, config);
        this.systemTenant = config.get("system-tenant").asString();
        this.defaultSalt = config.get("default-salt").asString("");
        this.acceptApiTokens = config.get("accept-api-tokens").asBoolean(true);
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
     * Flushes all cahes for the given tenant.
     *
     * @param tenant the tenant to flush
     */
    public static void flushCacheForTenant(Tenant<?> tenant) {
        tenantsCache.remove(tenant.getIdAsString());
        configCache.remove(tenant.getUniqueName());
        configCache.removeIf(cachedValue -> Strings.areEqual(cachedValue.getValue().getSecond(),
                                                             tenant.getUniqueName()));
    }

    @Override
    protected UserInfo findUserInSession(WebContext ctx) {
        UserInfo rootUser = super.findUserInSession(ctx);
        if (rootUser == null || defaultUser.equals(rootUser)) {
            return rootUser;
        }

        String spyId = ctx.getSessionValue(scope.getScopeId() + SPY_ID_SUFFIX).asString();
        if (Strings.isFilled(spyId)) {
            UserInfo spy = becomeSpyUser(spyId, rootUser);
            if (spy != null) {
                return spy;
            }
        }

        String tenantSpyId = ctx.getSessionValue(scope.getScopeId() + TENANT_SPY_ID_SUFFIX).asString();
        if (Strings.isFilled(tenantSpyId)) {
            return createUserWithTenant(rootUser, tenantSpyId);
        }

        return rootUser;
    }

    private UserInfo becomeSpyUser(String spyId, UserInfo rootUser) {
        U spyUser = fetchAccount(spyId, null);
        if (spyUser == null) {
            return null;
        }
        List<String> extraRoles = Lists.newArrayList();
        extraRoles.add(PERMISSION_SPY_USER);
        extraRoles.add(PERMISSION_SELECT_USER_ACCOUNT);
        if (rootUser.hasPermission(PERMISSION_SYSTEM_TENANT)) {
            extraRoles.add(PERMISSION_SYSTEM_TENANT);
        }
        return asUser(spyUser, extraRoles);
    }

    @Override
    public UserInfo createUserWithTenant(UserInfo originalUser, String tenantId) {
        if (Strings.isEmpty(tenantId) || Strings.areEqual(originalUser.getTenantId(), tenantId)) {
            return originalUser;
        }
        T tenant = fetchTenant(tenantId);
        if (tenant == null) {
            return originalUser;
        }

        // Copy all relevant data into a new object (outside of the cache)...
        U modifiedUser = cloneUser(originalUser);

        // And overwrite with the new tenant...
        modifiedUser.getTenant().setValue(tenant);

        Set<String> roles = computeRoles(modifiedUser, tenant, originalUser.hasPermission(PERMISSION_SYSTEM_TENANT));
        roles.add(PERMISSION_SPY_USER);
        roles.add(PERMISSION_SELECT_TENANT);
        return asUserWithRoles(modifiedUser, roles);
    }

    protected U cloneUser(UserInfo originalUser) {
        try {
            U currentUser = originalUser.getUserObject(getUserClass());
            U modifiedUser = getUserClass().getDeclaredConstructor().newInstance();
            modifiedUser.setId(currentUser.getId());
            modifiedUser.getUserAccountData()
                        .getLogin()
                        .setUsername(currentUser.getUserAccountData().getLogin().getUsername());
            modifiedUser.getUserAccountData().setEmail(currentUser.getUserAccountData().getEmail());
            modifiedUser.getUserAccountData()
                        .getPermissions()
                        .setConfigString(currentUser.getUserAccountData().getPermissions().getConfigString());
            modifiedUser.getUserAccountData()
                        .getPermissions()
                        .getPermissions()
                        .addAll(currentUser.getUserAccountData().getPermissions().getPermissions());
            return modifiedUser;
        } catch (Exception e) {
            throw Exceptions.handle(Log.APPLICATION, e);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    protected T fetchTenant(String tenantId) {
        return (T) tenantsCache.get(tenantId, this::loadTenant);
    }

    protected T loadTenant(String tenantId) {
        return mixing.getDescriptor(getTenantClass()).getMapper().find(getTenantClass(), tenantId).orElse(null);
    }

    /**
     * Determines the original tenant ID.
     *
     * @param ctx the current web request
     * @return the original tenant id of the currently active user (without spy overwrites)
     */
    public String getOriginalTenantId(WebContext ctx) {
        return ctx.getSessionValue(this.scope.getScopeId() + "-tenant-id").asString();
    }

    @Override
    public UserInfo findUserByName(@Nullable WebContext ctx, String user) {
        if (Strings.isEmpty(user)) {
            return null;
        }

        Optional<U> optionalAccount = loadAccountByName(user.toLowerCase());

        if (!optionalAccount.isPresent()) {
            return null;
        }
        if (optionalAccount.get().getUserAccountData().getLogin().isAccountLocked()) {
            throw Exceptions.createHandled().withNLSKey("LoginData.accountIsLocked").handle();
        }

        U account = fetchAccount(optionalAccount.get().getUniqueName(), optionalAccount.get());
        if (account == null) {
            return null;
        }
        return asUser(account, null);
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
        U account = fetchAccount(accountId, null);
        if (account == null) {
            return null;
        }
        return asUser(account, null);
    }

    @Nonnull
    @Override
    public UserInfo bindToRequest(@Nonnull WebContext ctx) {
        UserInfo info = super.bindToRequest(ctx);
        if (info.isLoggedIn()) {
            // We only need to verify the IP range for users being logged in....
            return verifyIpRange(ctx, info);
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
     * @param ctx  the current request
     * @param info the current user
     * @return the current user but possibly with less permissions
     */
    private UserInfo verifyIpRange(WebContext ctx, UserInfo info) {
        String actualUser = ctx.getSessionValue(scope.getScopeId() + "-user-id").asString();

        U account = fetchAccount(actualUser, null);

        if (account == null) {
            return defaultUser;
        }

        T tenant = account.getTenant().getValue();

        if (tenant != null && !tenant.getTenantData().matchesIPRange(ctx)) {
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

    /**
     * Tries to fetch the requested account from the cache.
     * <p>
     * If a new account from the database is present (during login) this can be passed in as <tt>accountFromDB</tt>. If
     * not, the value can be left <tt>null</tt> and a lookup will be performed if necessary. This ensures, that the
     * cache is updated if a stale entry is detected during login.
     *
     * @param accountId    the id of the account to fetch
     * @param givenAccount a fresh version from the database to check cache integrity
     * @return the most current version from the cache to re-use computed fields if possible
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private U fetchAccount(@Nonnull String accountId, @Nullable U givenAccount) {
        U account;
        U accountFromDB = givenAccount;
        U accountFromCache = (U) userAccountCache.get(accountId);
        if (accountFromCache == null || (accountFromDB != null
                                         && accountFromDB.getVersion() > accountFromCache.getVersion())) {
            if (accountFromDB == null) {
                accountFromDB = loadAccount(accountId);
                if (accountFromDB == null) {
                    return null;
                }
            }
            userAccountCache.put(accountFromDB.getUniqueName(), accountFromDB);
            rolesCache.remove(accountFromDB.getUniqueName());
            configCache.remove(accountFromDB.getUniqueName());
            account = accountFromDB;
        } else {
            account = accountFromCache;
        }
        account.getTenant().setValue(fetchTenant(account, accountFromDB));

        return account;
    }

    @SuppressWarnings("unchecked")
    protected U loadAccount(@Nonnull String accountId) {
        return (U) mixing.getDescriptor(getUserClass()).getMapper().resolve(accountId).orElse(null);
    }

    protected T loadTenantOfAccount(U account) {
        return mixing.getDescriptor(getTenantClass())
                     .getMapper()
                     .find(getTenantClass(), account.getTenant().getId())
                     .orElseThrow(() -> new IllegalStateException(Strings.apply("Tenant %s for UserAccount %s vanished!",
                                                                                account.getTenant().getId(),
                                                                                account.getId())));
    }

    @SuppressWarnings("unchecked")
    private T fetchTenant(U account, @Nullable U accountFromDB) {
        T tenantFromCache = (T) tenantsCache.get(String.valueOf(account.getTenant().getId()));

        // If we found a tenant and no database object is present - we don't need
        // to check for updates or changes, just return the cached value.
        if (tenantFromCache != null && accountFromDB == null) {
            return tenantFromCache;
        }

        // ...otherwise, let's check if our cached instance is still up to date
        T tenantFromDB = loadTenantOfAccount(account);

        // Only actually use the instance from the database if we either have no cached value
        // or if it is outdated. Otherwise the cached instance is preferred, because it might contain
        // useful pre-computed values...
        if (tenantFromCache == null || tenantFromDB.getVersion() > tenantFromCache.getVersion()) {
            tenantsCache.put(tenantFromDB.getIdAsString(), tenantFromDB);

            // We also need to re-compute the roles and config of the user
            // as this is also determined by the tenant
            rolesCache.remove(account.getUniqueName());
            configCache.remove(account.getUniqueName());
            configCache.remove(tenantFromDB.getUniqueName());

            return tenantFromDB;
        }

        return tenantFromCache;
    }

    protected UserInfo asUser(U account, List<String> extraRoles) {
        Set<String> roles = computeRoles(null, account.getUniqueName());
        if (extraRoles != null) {
            // Make a copy so that we do not modify the cached set...
            roles = Sets.newTreeSet(roles);
            roles.addAll(extraRoles);
        }
        return asUserWithRoles(account, roles);
    }

    private UserInfo asUserWithRoles(U account, Set<String> roles) {
        return UserInfo.Builder.createUser(account.getUniqueName())
                               .withUsername(account.getUserAccountData().getLogin().getUsername())
                               .withTenantId(String.valueOf(account.getTenant().getId()))
                               .withTenantName(account.getTenant().getValue().getTenantData().getName())
                               .withLang(NLS.getDefaultLanguage())
                               .withPermissions(roles)
                               .withSettingsSupplier(ui -> getUserSettings(getScopeSettings(), ui))
                               .withUserSupplier(u -> account)
                               .build();
    }

    @Override
    public UserInfo findUserByCredentials(@Nullable WebContext ctx, String user, String password) {
        if (Strings.isEmpty(password)) {
            return null;
        }

        UserInfo result = findUserByName(ctx, user);
        if (result == null) {
            auditLog.negative("AuditLog.lockedOrNonexitentUserTriedLogin").forUser(null, user).log();
            return null;
        }

        U account = result.getUserObject(getUserClass());
        if (account.getUserAccountData().isExternalLoginRequired() && !isWithinInterval(account.getUserAccountData()
                                                                                               .getLogin()
                                                                                               .getLastExternalLogin(),
                                                                                        account.getTenant()
                                                                                               .getValue()
                                                                                               .getTenantData()
                                                                                               .getExternalLoginIntervalDays())) {
            completeAuditLogForUser(auditLog.negative("AuditLog.externalLoginRequired"), account);
            throw Exceptions.createHandled().withNLSKey("UserAccount.externalLoginMustBePerformed").handle();
        }

        LoginData loginData = account.getUserAccountData().getLogin();
        if (acceptApiTokens && Strings.areEqual(password, loginData.getApiToken())) {
            completeAuditLogForUser(auditLog.neutral("AuditLog.apiTokenLogin"), account);
            return result;
        }

        if (loginData.checkPassword(password, defaultSalt)) {
            auditLog.neutral("AuditLog.passwordLogin")
                    .causedByUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                    .forUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                    .forTenant(String.valueOf(account.getTenant().getId()),
                               account.getTenant().getValue().getTenantData().getName())
                    .log();
            return result;
        }

        completeAuditLogForUser(auditLog.negative("AuditLog.loginRejected"), account);

        return null;
    }

    protected void completeAuditLogForUser(AuditLog.AuditLogBuilder builder, U account) {
        builder.causedByUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
               .forUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
               .forTenant(String.valueOf(account.getTenant().getId()),
                          account.getTenant().getValue().getTenantData().getName())
               .log();
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
        return userAccount.getUserAccountData().getLogin().checkPassword(password, defaultSalt);
    }

    @Override
    protected void recordUserLogin(WebContext ctx, UserInfo user) {
        recordLogin(user, false);
    }

    /**
     * Handles an external login (e.g. via SAML).
     *
     * @param ctx  the current request
     * @param user the user which logged in
     */
    public void onExternalLogin(WebContext ctx, UserInfo user) {
        updateLoginCookie(ctx, user);
        recordLogin(user, true);
    }

    protected abstract void recordLogin(UserInfo user, boolean external);

    @Override
    protected U getUserObject(UserInfo userInfo) {
        return fetchAccount(userInfo.getUserId(), null);
    }

    @Override
    protected UserSettings getUserSettings(UserSettings scopeSettings, UserInfo userInfo) {
        U user = userInfo.getUserObject(getUserClass());
        if (user.getUserAccountData().getPermissions().getConfig() == null) {
            if (user.getTenant().getValue().getTenantData().getPermissions().getConfig() == null) {
                return scopeSettings;
            }

            return configCache.get(user.getTenant().getUniqueObjectName(), i -> {
                Config cfg = scopeSettings.getConfig();
                cfg = user.getTenant().getValue().getTenantData().getPermissions().getConfig().withFallback(cfg);
                return Tuple.create(new UserSettings(cfg), user.getTenant().getUniqueObjectName());
            }).getFirst();
        }

        return configCache.get(user.getUniqueName(), i -> {
            Config cfg = scopeSettings.getConfig();
            cfg = user.getTenant().getValue().getTenantData().getPermissions().getConfig().withFallback(cfg);
            cfg = user.getUserAccountData().getPermissions().getConfig().withFallback(cfg);
            return Tuple.create(new UserSettings(cfg), user.getTenant().getUniqueObjectName());
        }).getFirst();
    }

    @Override
    public void updateLoginCookie(WebContext ctx, UserInfo user, boolean keepLogin) {
        super.updateLoginCookie(ctx, user, keepLogin);
        installFingerprintInSession(ctx,
                                    user.getUserObject(UserAccount.class)
                                        .getUserAccountData()
                                        .getLogin()
                                        .getFingerprint());
    }

    /**
     * Installs the given fingerprint into the session of the given request.
     * <p>
     * This is made externally available so that the {@link ProfileController} can instantly
     * update the fingerprint after the user changed its password. Otherwise an
     * immediate logout would happen which would be kind of a strange user experience.
     *
     * @param ctx         the request used to update the session cookie
     * @param fingerprint the new fingerprint to use
     */
    public void installFingerprintInSession(WebContext ctx, String fingerprint) {
        ctx.setSessionValue(scope.getScopeId() + SUFFIX_FINGERPRINT, fingerprint);
    }

    @Override
    @SuppressWarnings({"squid:S1126", "RedundantIfStatement"})
    @Explain("Using explicit abort conditions and a final true makes all checks obvious")
    protected boolean isUserStillValid(String userId, WebContext ctx) {
        U user = fetchAccount(userId, null);

        if (user == null) {
            return false;
        }

        LoginData loginData = user.getUserAccountData().getLogin();
        TenantData tenantData = user.getTenant().getValue().getTenantData();

        if (loginData.isAccountLocked()) {
            return false;
        }

        String fingerprintInSession = ctx.getSessionValue(scope.getScopeId() + SUFFIX_FINGERPRINT).asString();
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
        Set<String> roles = Sets.newTreeSet();
        roles.addAll(user.getUserAccountData().getPermissions().getPermissions());
        roles.addAll(tenant.getTenantData().getPermissions().getPermissions());
        roles.add(UserInfo.PERMISSION_LOGGED_IN);
        Set<String> transformedRoles = transformRoles(roles);
        if (isSystemTenant && transformedRoles.contains(PERMISSION_MANAGE_SYSTEM)) {
            roles.add(PERMISSION_SYSTEM_TENANT);
            return transformRoles(roles);
        }
        return transformedRoles;
    }

    @Override
    protected Set<String> computeRoles(WebContext ctx, String userId) {
        Set<String> cachedRoles = rolesCache.get(userId);
        if (cachedRoles != null) {
            return cachedRoles;
        }

        U user = fetchAccount(userId, null);
        Set<String> roles;
        if (user != null) {
            roles = computeRoles(user,
                                 user.getTenant().getValue(),
                                 Strings.areEqual(systemTenant, String.valueOf(user.getTenant().getValue().getId())));
        } else {
            roles = Collections.emptySet();
        }

        rolesCache.put(userId, roles);
        return roles;
    }

    @Nonnull
    @Override
    protected String computeUsername(@Nullable WebContext ctx, String userId) {
        U account = fetchAccount(userId, null);
        if (account == null) {
            return "(unknown)";
        } else {
            return account.getUserAccountData().getLogin().getUsername();
        }
    }

    @Nonnull
    @Override
    protected String computeTenantname(@Nullable WebContext ctx, String tenantId) {
        T tenant = fetchTenant(tenantId);
        if (tenant == null) {
            return "(unknown)";
        } else {
            return tenant.getTenantData().getName();
        }
    }

    @Nonnull
    @Override
    protected String computeLang(WebContext ctx, String userId) {
        return NLS.getDefaultLanguage();
    }
}
