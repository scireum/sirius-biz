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
import sirius.biz.web.BizController;
import sirius.db.mixing.Entity;
import sirius.db.mixing.OMA;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.extensions.Extension;
import sirius.kernel.health.Exceptions;
import sirius.web.http.WebContext;
import sirius.web.security.GenericUserManager;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserInfo;
import sirius.web.security.UserManager;
import sirius.web.security.UserManagerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Collections;
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
 */
public class TenantUserManager extends GenericUserManager {

    /**
     * This flag permission is granted to all users which belong to the system tenant.
     * <p>
     * The id of the system tenant can be set in the scope config. The system tenant usually is the administrative
     * company which owns / runs the system.
     */
    public static final String PERMISSION_SYSTEM_TENANT = "flag-system-tenant";

    /**
     * This flag indicates that the current user either has taken control over another tenant or use account.
     */
    public static final String PERMISSION_SPY_USER = "flag-spy-user";

    /**
     * If a session-value named {@code UserContext.getCurrentScope().getScopeId() +
     * TenantUserManager.TENANT_SPY_ID_SUFFIX}
     * is present, the user will belong to the given tenant and not to his own one.
     * <p>
     * This is used by support and administrative tasks. Beware, that the id is not checked, so the one who installs
     * the
     * ID has to verify that the user is allowed to switch to this tenant.
     */
    public static final String TENANT_SPY_ID_SUFFIX = "-tenant-spy-id";

    /**
     * If a session-value named {@code UserContext.getCurrentUser().getUserId() + TenantUserManager.SPY_ID_SUFFIX}
     * is present, the user with the given ID will be used, instead of the current one.
     * <p>
     * This is used by support and administrative tasks. Beware, that the id is not checked, so the one who installs the
     * ID has to verify that the user is allowed to become this user.
     */
    private static final String SPY_ID_SUFFIX = "-spy-id";

    private final String systemTenant;
    private final String defaultSalt;
    private final boolean acceptApiTokens;
    private final boolean checkPasswordsCaseInsensitive;
    private final boolean autocreateTenant;

    /**
     * Creates a new user manager for the given scope and configuration.
     */
    @Framework("tenants")
    @Register(name = "tenants")
    public static class Factory implements UserManagerFactory {

        @Nonnull
        @Override
        public UserManager createManager(@Nonnull ScopeInfo scope, @Nonnull Extension config) {
            return new TenantUserManager(scope, config);
        }
    }

    @Part
    private static OMA oma;

    private static Cache<String, Set<String>> rolesCache = CacheManager.createCache("tenants-roles");
    private static Cache<String, UserAccount> userAccountCache = CacheManager.createCache("tenants-users");
    private static Cache<String, Tenant> tenantsCache = CacheManager.createCache("tenants-tenants");
    private static Cache<String, Config> configCache = CacheManager.createCache("tenants-configs");

    /**
     * Flushes all caches for the given account.
     *
     * @param account the account to flush
     */
    public static void flushCacheForUserAccount(UserAccount account) {
        rolesCache.remove(account.getUniqueName());
        userAccountCache.remove(account.getIdAsString());
        configCache.remove(account.getIdAsString());
    }

    /**
     * Flushes all cahes for the given tenant.
     *
     * @param tenant the tenant to flush
     */
    public static void flushCacheForTenant(Tenant tenant) {
        tenantsCache.remove(tenant.getIdAsString());
        configCache.clear();
    }

    protected TenantUserManager(ScopeInfo scope, Extension config) {
        super(scope, config);
        this.sessionStorage = SESSION_STORAGE_TYPE_CLIENT;
        this.systemTenant = config.get("system-tenant").asString();
        this.defaultSalt = config.get("default-salt").asString("");
        this.acceptApiTokens = config.get("accept-api-tokens").asBoolean(true);
        this.checkPasswordsCaseInsensitive = config.get("check-passwords-case-insensitive").asBoolean(false);
        this.autocreateTenant = config.get("autocreate-tenant").asBoolean(true);
    }

    @Override
    protected UserInfo findUserInSession(WebContext ctx) {
        UserInfo rootUser = super.findUserInSession(ctx);
        if (rootUser != null && rootUser != defaultUser) {
            String spyId = ctx.getSessionValue(scope.getScopeId() + SPY_ID_SUFFIX).asString();
            if (Strings.isFilled(spyId)) {
                UserAccount spyUser = fetchAccount(spyId, null);
                if (spyUser != null) {
                    List<String> extraRoles = Lists.newArrayList();
                    extraRoles.add(PERMISSION_SPY_USER);
                    if (rootUser.hasPermission(PERMISSION_SYSTEM_TENANT)) {
                        extraRoles.add(PERMISSION_SYSTEM_TENANT);
                    }
                    return asUser(spyUser, extraRoles);
                }
            }

            String tenantSpyId = ctx.getSessionValue(scope.getScopeId() + TENANT_SPY_ID_SUFFIX).asString();
            if (Strings.isFilled(tenantSpyId)) {
                Tenant tenant = tenantsCache.get(tenantSpyId, i -> oma.find(Tenant.class, i).orElse(null));
                if (tenant != null) {
                    // Copy all relevant data into a new object (outside of the cache)...
                    UserAccount currentUser = rootUser.getUserObject(UserAccount.class);
                    UserAccount modifiedUser = new UserAccount();
                    modifiedUser.setId(currentUser.getId());
                    modifiedUser.getLogin().setUsername(currentUser.getLogin().getUsername());
                    modifiedUser.setEmail(currentUser.getEmail());
                    modifiedUser.getPermissions().setConfigString(currentUser.getPermissions().getConfigString());
                    modifiedUser.getPermissions()
                                .getPermissions()
                                .addAll(currentUser.getPermissions().getPermissions());

                    // And overwrite with the new tenant...
                    modifiedUser.getTenant().setValue(tenant);

                    List<String> extraRoles = Lists.newArrayList();
                    extraRoles.add(PERMISSION_SPY_USER);
                    if (rootUser.hasPermission(PERMISSION_SYSTEM_TENANT)) {
                        extraRoles.add(PERMISSION_SYSTEM_TENANT);
                    }
                    return asUser(modifiedUser, extraRoles);
                }
            }
        }

        return rootUser;
    }

    @Override
    protected UserInfo findUserByName(WebContext webContext, String user) {
        if (Strings.isEmpty(user)) {
            return null;
        }

        Optional<UserAccount> optionalAccount =
                oma.select(UserAccount.class).eq(UserAccount.LOGIN.inner(LoginData.USERNAME), user).one();

        if (!optionalAccount.isPresent()) {
            optionalAccount = createSystemTenantIfNonExistent();
            if (!optionalAccount.isPresent()) {
                return null;
            }
        }
        if (optionalAccount.get().getLogin().isAccountLocked()) {
            throw Exceptions.createHandled().withNLSKey("LoginData.accountIsLocked").handle();
        }

        UserAccount account = fetchAccount(optionalAccount.get().getUniqueName(), optionalAccount.get());
        if (account == null) {
            return null;
        }
        return asUser(account, null);
    }

    /**
     * Tries to find a {@link UserInfo} for the given unique object name of a {@link UserAccount}.
     *
     * @param accountId the unique object name of an <tt>UserAccount</tt> to resolve into a <tt>UserInfo</tt>
     * @return the <tt>UserInfo</tt> representing the given account (will utilize caches if available) or <tt>null</tt>
     * if no such user exists
     * @see Entity#getUniqueName()
     */
    @Nullable
    public UserInfo findUserByUserId(String accountId) {
        UserAccount account = fetchAccount(accountId, null);
        if (account == null) {
            return null;
        }
        return asUser(account, null);
    }

    /**
     * Tries to fetch the requested account from the cache.
     * <p>
     * If a new account from the database is present (during login) this can be passed in as <tt>accountFromDB</tt>. If
     * not, the value can be left <tt>null</tt> and a lookup will be performed if necessary. This ensures, that the
     * cache is updated if a stale entry is detected during login.
     *
     * @param accountId     the id of the account to fetch
     * @param accountFromDB a fresh version from the database to check cache integrity
     * @return the most current version from the cache to re-use computed fields if possible
     */
    @Nullable
    private UserAccount fetchAccount(@Nonnull String accountId, @Nullable UserAccount accountFromDB) {
        UserAccount account;
        UserAccount accountFromCache = userAccountCache.get(accountId);
        if (accountFromCache == null || (accountFromDB != null
                                         && accountFromDB.getVersion() > accountFromCache.getVersion())) {
            if (accountFromDB == null) {
                accountFromDB = (UserAccount) oma.resolve(accountId).orElse(null);
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
        fetchTenant(account, accountFromDB);

        return account;
    }

    private Tenant fetchTenant(UserAccount account, @Nullable UserAccount accountFromDB) {
        Tenant tenantFromCache = tenantsCache.get(String.valueOf(account.getTenant().getId()));

        // if we found a tenant and no database object is present - we don't need
        // to check for updates or changes, just return the cached value.
        if (tenantFromCache != null && accountFromDB == null) {
            return tenantFromCache;
        }

        // ...otherwise, let's check if our cached instance is stil up to date
        Tenant tenantFromDB = oma.find(Tenant.class, account.getTenant().getId())
                                 .orElseThrow(() -> new IllegalStateException(Strings.apply(
                                         "Tenant %s for UserAccount %s vanished!",
                                         account.getTenant().getId(),
                                         account.getId())));

        // Only actually use the instance from the database if we either have no cached value
        // or if it is outdated. Otherwise the cached instance is preferred, because it might contain
        // useful pre-computed values...
        if (tenantFromCache == null || tenantFromDB.getVersion() > tenantFromCache.getVersion()) {
            tenantsCache.put(tenantFromDB.getIdAsString(), tenantFromDB);

            // We also need to re-compute the roles and config of the user
            // as this is also determined by the tenant
            rolesCache.remove(account.getUniqueName());
            configCache.remove(account.getUniqueName());

            return tenantFromDB;
        }

        return tenantFromCache;
    }

    private Optional<UserAccount> createSystemTenantIfNonExistent() {
        try {
            if (autocreateTenant && !oma.select(Tenant.class).exists()) {
                BizController.LOG.INFO("No tenant is present, creating system tenant....");
                Tenant tenant = new Tenant();
                tenant.setName("System Tenant");
                oma.update(tenant);

                BizController.LOG.INFO(
                        "No user account is present, creating system / system - Please change the password now!");
                UserAccount ua = new UserAccount();
                ua.getTenant().setValue(oma.select(Tenant.class).orderAsc(Tenant.ID).queryFirst());
                ua.getLogin().setUsername("system");
                ua.getLogin().setCleartextPassword("system");
                oma.update(ua);

                return Optional.of(ua);
            }
        } catch (Throwable e) {
            Exceptions.handle()
                      .to(BizController.LOG)
                      .error(e)
                      .withSystemErrorMessage("Cannot initialize tenants or user accounts: %s (%s)")
                      .handle();
        }

        return Optional.empty();
    }

    protected UserInfo asUser(UserAccount account, List<String> extraRoles) {
        Set<String> roles = computeRoles(null, String.valueOf(account.getUniqueName()));
        if (extraRoles != null) {
            // Make a copy so that we do not modify the cached set...
            roles = Sets.newTreeSet(roles);
            roles.addAll(extraRoles);
        }
        return UserInfo.Builder.createUser(account.getUniqueName())
                               .withUsername(account.getLogin().getUsername())
                               .withTenantId(String.valueOf(account.getTenant().getId()))
                               .withTenantName(account.getTenant().getValue().getName())
                               .withEmail(account.getEmail())
                               .withLang("de")
                               .withPermissions(roles)
                               .withConfigSupplier(ui -> getUserConfig(getScopeConfig(), ui))
                               .withUserSupplier(u -> account)
                               .build();
    }

    @Override
    protected UserInfo findUserByCredentials(WebContext webContext, String user, String password) {
        if (Strings.isEmpty(password)) {
            return null;
        }

        UserInfo result = findUserByName(webContext, user);
        if (result == null) {
            return null;
        }

        LoginData loginData = result.getUserObject(UserAccount.class).getLogin();
        if (acceptApiTokens && Strings.areEqual(password, loginData.getApiToken())) {
            return result;
        }

        String salt = Value.of(loginData.getSalt()).asString(defaultSalt);
        String givenPasswordHash = LoginData.hashPassword(salt, password);
        if (givenPasswordHash.equals(loginData.getPasswordHash())) {
            return result;
        }

        String givenUcasePasswordHash = LoginData.hashPassword(salt, password.toUpperCase());
        if (checkPasswordsCaseInsensitive && givenUcasePasswordHash.equals(loginData.getUcasePasswordHash())) {
            return result;
        }

        return null;
    }

    @Override
    protected void recordUserLogin(WebContext ctx, UserInfo user) {
        try {
            UserAccount account = (UserAccount) getUserObject(user);
            // This should never happen (other than manually changed or tampered database data).
            // However, this would lead to an endless recursion, so we skip right here....
            if (account.getTrace().getCreatedAt() == null) {
                return;
            }
            account.getTrace().setSilent(true);
            account.getJournal().setSilent(true);
            account.getLogin().setNumberOfLogins(account.getLogin().getNumberOfLogins() + 1);
            account.getLogin().setLastLogin(LocalDateTime.now());
            oma.override(account);
        } catch (Throwable e) {
            Exceptions.handle(BizController.LOG, e);
        }
    }

    @Override
    protected Object getUserObject(UserInfo userInfo) {
        return fetchAccount(userInfo.getUserId(), null);
    }

    @Override
    protected Config getUserConfig(Config config, UserInfo userInfo) {
        UserAccount user = userInfo.getUserObject(UserAccount.class);
        if (user.getPermissions().getConfig() == null) {
            if (user.getTenant().getValue().getPermissions().getConfig() == null) {
                return config;
            }
        }

        return configCache.get(userInfo.getUserId(), i -> {
            Config cfg = config;
            if (user.getTenant().getValue().getPermissions().getConfig() != null) {
                cfg = user.getTenant().getValue().getPermissions().getConfig().withFallback(cfg);
            }
            if (user.getPermissions().getConfig() != null) {
                cfg = user.getPermissions().getConfig().withFallback(cfg);
            }

            return cfg;
        });
    }

    @Override
    protected boolean isUserStillValid(String userId) {
        UserAccount user = fetchAccount(userId, null);
        return user != null && !user.getLogin().isAccountLocked();
    }

    private Set<String> computeRoles(UserAccount user, Tenant tenant) {
        Set<String> roles = Sets.newTreeSet();
        roles.addAll(user.getPermissions().getPermissions());
        roles.addAll(tenant.getPermissions().getPermissions());
        if (Strings.areEqual(systemTenant, String.valueOf(tenant.getId()))) {
            roles.add(PERMISSION_SYSTEM_TENANT);
        }
        roles.add(UserInfo.PERMISSION_LOGGED_IN);
        roles = transformRoles(roles, false);
        return roles;
    }

    @Override
    protected Set<String> computeRoles(WebContext ctx, String userId) {
        Set<String> cachedRoles = rolesCache.get(userId);
        if (cachedRoles != null) {
            return cachedRoles;
        }

        UserAccount user = fetchAccount(userId, null);
        Set<String> roles;
        if (user != null) {
            roles = computeRoles(user, user.getTenant().getValue());
        } else {
            roles = Collections.emptySet();
        }

        rolesCache.put(userId, roles);
        return roles;
    }
}
