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
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLQuery;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.web.http.WebContext;
import sirius.web.security.GenericUserManager;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserInfo;
import sirius.web.security.UserManager;
import sirius.web.security.UserManagerFactory;
import sirius.web.security.UserSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
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

    private final String systemTenant;
    private final String defaultSalt;
    private final boolean acceptApiTokens;
    private final boolean autocreateTenant;

    @Part
    private static OMA oma;

    @Part
    private static Mixing mixing;

    @Part
    private static AuditLog auditLog;

    private static Cache<String, Set<String>> rolesCache = CacheManager.createCoherentCache("tenants-roles");
    private static Cache<String, UserAccount> userAccountCache = CacheManager.createCoherentCache("tenants-users");
    private static Cache<String, Tenant> tenantsCache = CacheManager.createCoherentCache("tenants-tenants");

    private static Cache<String, Tuple<UserSettings, String>> configCache =
            CacheManager.createCoherentCache("tenants-configs");

    protected TenantUserManager(ScopeInfo scope, Extension config) {
        super(scope, config);
        this.systemTenant = config.get("system-tenant").asString();
        this.defaultSalt = config.get("default-salt").asString("");
        this.acceptApiTokens = config.get("accept-api-tokens").asBoolean(true);
        this.autocreateTenant = config.get("autocreate-tenant").asBoolean(true);
    }

    /**
     * Creates a new user manager for the given scope and configuration.
     */
    @Register(name = "tenants", framework = Tenants.FRAMEWORK_TENANTS)
    public static class Factory implements UserManagerFactory {

        @Nonnull
        @Override
        public UserManager createManager(@Nonnull ScopeInfo scope, @Nonnull Extension config) {
            return new TenantUserManager(scope, config);
        }
    }

    /**
     * Flushes all caches for the given account.
     *
     * @param account the account to flush
     */
    public static void flushCacheForUserAccount(UserAccount account) {
        rolesCache.remove(account.getUniqueName());
        userAccountCache.remove(account.getUniqueName());
        configCache.remove(account.getUniqueName());
    }

    /**
     * Flushes all cahes for the given tenant.
     *
     * @param tenant the tenant to flush
     */
    public static void flushCacheForTenant(Tenant tenant) {
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
        UserAccount spyUser = fetchAccount(spyId, null);
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
        Tenant tenant = fetchTenant(tenantId);
        if (tenant == null) {
            return originalUser;
        }

        // Copy all relevant data into a new object (outside of the cache)...
        UserAccount currentUser = originalUser.getUserObject(UserAccount.class);
        UserAccount modifiedUser = new UserAccount();
        modifiedUser.setId(currentUser.getId());
        modifiedUser.getLogin().setUsername(currentUser.getLogin().getUsername());
        modifiedUser.setEmail(currentUser.getEmail());
        modifiedUser.getPermissions().setConfigString(currentUser.getPermissions().getConfigString());
        modifiedUser.getPermissions().getPermissions().addAll(currentUser.getPermissions().getPermissions());

        // And overwrite with the new tenant...
        modifiedUser.getTenant().setValue(tenant);

        Set<String> roles = computeRoles(modifiedUser, tenant, originalUser.hasPermission(PERMISSION_SYSTEM_TENANT));
        roles.add(PERMISSION_SPY_USER);
        roles.add(PERMISSION_SELECT_TENANT);
        return asUserWithRoles(modifiedUser, roles);
    }

    @Nullable
    private Tenant fetchTenant(String tenantId) {
        return tenantsCache.get(tenantId, i -> oma.find(Tenant.class, i).orElse(null));
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

        Optional<UserAccount> optionalAccount =
                oma.select(UserAccount.class).eq(UserAccount.LOGIN.inner(LoginData.USERNAME), user.toLowerCase()).one();

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
     * @param accountId the unique object name of an <tt>UserAccount</tt> to resolveFromString into a <tt>UserInfo</tt>
     * @return the <tt>UserInfo</tt> representing the given account (will utilize caches if available) or <tt>null</tt>
     * if no such user exists
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
     * @param accountId    the id of the account to fetch
     * @param givenAccount a fresh version from the database to check cache integrity
     * @return the most current version from the cache to re-use computed fields if possible
     */
    @Nullable
    private UserAccount fetchAccount(@Nonnull String accountId, @Nullable UserAccount givenAccount) {
        UserAccount account;
        UserAccount accountFromDB = givenAccount;
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
        account.getTenant().setValue(fetchTenant(account, accountFromDB));

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
            configCache.remove(tenantFromDB.getUniqueName());

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
                tenant.getTrace().setSilent(true);
                oma.update(tenant);

                BizController.LOG.INFO(
                        "No user account is present, creating system / system - Please change the password now!");
                UserAccount ua = new UserAccount();
                ua.getTenant().setValue(oma.select(Tenant.class).orderAsc(Tenant.ID).queryFirst());
                ua.setEmail("system@localhost.local");
                ua.getLogin().setUsername("system");
                ua.getLogin().setCleartextPassword("system");
                ua.getTrace().setSilent(true);
                // This should be enough to grant us more roles via the UI
                ua.getPermissions().getPermissions().add("administrator");
                ua.getPermissions().getPermissions().add("user-administrator");
                oma.update(ua);

                return Optional.of(ua);
            }
        } catch (Exception e) {
            Exceptions.handle()
                      .to(BizController.LOG)
                      .error(e)
                      .withSystemErrorMessage("Cannot initialize tenants or user accounts: %s (%s)")
                      .handle();
        }

        return Optional.empty();
    }

    protected UserInfo asUser(UserAccount account, List<String> extraRoles) {
        Set<String> roles = computeRoles(null, account.getUniqueName());
        if (extraRoles != null) {
            // Make a copy so that we do not modify the cached set...
            roles = Sets.newTreeSet(roles);
            roles.addAll(extraRoles);
        }
        return asUserWithRoles(account, roles);
    }

    private UserInfo asUserWithRoles(UserAccount account, Set<String> roles) {
        return UserInfo.Builder.createUser(account.getUniqueName())
                               .withUsername(account.getLogin().getUsername())
                               .withTenantId(String.valueOf(account.getTenant().getId()))
                               .withTenantName(account.getTenant().getValue().getName())
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

        UserAccount account = result.getUserObject(UserAccount.class);
        if (account.isExternalLoginRequired() && !isWithinInterval(account.getLogin().getLastExternalLogin(),
                                                                   account.getTenant()
                                                                          .getValue()
                                                                          .getExternalLoginIntervalDays())) {
            auditLog.negative("AuditLog.externalLoginRequired")
                    .causedByUser(account.getUniqueName(), account.getLogin().getUsername())
                    .forUser(account.getUniqueName(), account.getLogin().getUsername())
                    .forTenant(String.valueOf(account.getTenant().getId()), account.getTenant().getValue().getName())
                    .log();
            throw Exceptions.createHandled().withNLSKey("UserAccount.externalLoginMustBePerformed").handle();
        }

        LoginData loginData = account.getLogin();
        if (acceptApiTokens && Strings.areEqual(password, loginData.getApiToken())) {
            auditLog.neutral("AuditLog.apiTokenLogin")
                    .causedByUser(account.getUniqueName(), account.getLogin().getUsername())
                    .forUser(account.getUniqueName(), account.getLogin().getUsername())
                    .forTenant(String.valueOf(account.getTenant().getId()), account.getTenant().getValue().getName())
                    .log();
            return result;
        }

        if (loginData.checkPassword(password, defaultSalt)) {
            auditLog.neutral("AuditLog.passwordLogin")
                    .causedByUser(account.getUniqueName(), account.getLogin().getUsername())
                    .forUser(account.getUniqueName(), account.getLogin().getUsername())
                    .forTenant(String.valueOf(account.getTenant().getId()), account.getTenant().getValue().getName())
                    .log();
            return result;
        }

        auditLog.negative("AuditLog.loginRejected")
                .forUser(account.getUniqueName(), account.getLogin().getUsername())
                .forTenant(String.valueOf(account.getTenant().getId()), account.getTenant().getValue().getName())
                .log();

        return null;
    }

    /**
     * Checks if the given password of the given {@link UserAccount}  is correct.
     *
     * @param userAccount the user account to validate the password for
     * @param password    the password to validate
     * @return <tt>true</tt> if the password is valid, <tt>false</tt> otherwise
     */
    public boolean checkPassword(UserAccount userAccount, String password) {
        return userAccount.getLogin().checkPassword(password, defaultSalt);
    }

    @Override
    protected void recordUserLogin(WebContext ctx, UserInfo user) {
        recordLogin(user, false);
    }

    /**
     * Handles an external login (e.g. via SAML).
     *
     * @param user the user which logged in
     */
    public void onExternalLogin(WebContext ctx, UserInfo user) {
        updateLoginCookie(ctx, user);
        recordLogin(user, true);
    }

    protected void recordLogin(UserInfo user, boolean external) {
        try {
            UserAccount account = (UserAccount) getUserObject(user);
            EntityDescriptor ed = mixing.getDescriptor(UserAccount.class);

            String numberOfLoginsField =
                    ed.getProperty(UserAccount.LOGIN.inner(LoginData.NUMBER_OF_LOGINS)).getPropertyName();
            String lastLoginField = ed.getProperty(UserAccount.LOGIN.inner(LoginData.LAST_LOGIN)).getPropertyName();

            if (external) {
                String lastExternalLoginField =
                        ed.getProperty(UserAccount.LOGIN.inner(LoginData.LAST_EXTERNAL_LOGIN)).getPropertyName();
                SQLQuery qry = oma.getDatabase(Mixing.DEFAULT_REALM)
                                  .createQuery("UPDATE "
                                               + ed.getRelationName()
                                               + " SET "
                                               + numberOfLoginsField
                                               + " = "
                                               + numberOfLoginsField
                                               + " + 1, "
                                               + lastExternalLoginField
                                               + " = ${lastExternalLogin}, "
                                               + lastLoginField
                                               + " = ${lastLogin} WHERE id = ${id}");
                qry.set("lastExternalLogin", LocalDateTime.now());
                qry.set("lastLogin", LocalDateTime.now());
                qry.set("id", account.getId());
                qry.executeUpdate();
            } else {
                SQLQuery qry = oma.getDatabase(Mixing.DEFAULT_REALM)
                                  .createQuery("UPDATE "
                                               + ed.getRelationName()
                                               + " SET "
                                               + numberOfLoginsField
                                               + " = "
                                               + numberOfLoginsField
                                               + " + 1, "
                                               + lastLoginField
                                               + " = ${lastLogin} WHERE id = ${id}");
                qry.set("lastLogin", LocalDateTime.now());
                qry.set("id", account.getId());
                qry.executeUpdate();
            }
        } catch (Exception e) {
            Exceptions.handle(BizController.LOG, e);
        }
    }

    @Override
    protected Object getUserObject(UserInfo userInfo) {
        return fetchAccount(userInfo.getUserId(), null);
    }

    @Override
    protected UserSettings getUserSettings(UserSettings scopeSettings, UserInfo userInfo) {
        UserAccount user = userInfo.getUserObject(UserAccount.class);
        if (user.getPermissions().getConfig() == null) {
            if (user.getTenant().getValue().getPermissions().getConfig() == null) {
                return scopeSettings;
            }

            return configCache.get(user.getTenant().getUniqueObjectName(), i -> {
                Config cfg = scopeSettings.getConfig();
                cfg = user.getTenant().getValue().getPermissions().getConfig().withFallback(cfg);
                return Tuple.create(new UserSettings(cfg), user.getTenant().getUniqueObjectName());
            }).getFirst();
        }

        return configCache.get(user.getUniqueName(), i -> {
            Config cfg = scopeSettings.getConfig();
            cfg = user.getTenant().getValue().getPermissions().getConfig().withFallback(cfg);
            cfg = user.getPermissions().getConfig().withFallback(cfg);
            return Tuple.create(new UserSettings(cfg), user.getTenant().getUniqueObjectName());
        }).getFirst();
    }

    @Override
    @SuppressWarnings({"squid:S1126", "RedundantIfStatement"})
    @Explain("Using explicit abort conditions and a final true makes all checks obvious")
    protected boolean isUserStillValid(String userId) {
        UserAccount user = fetchAccount(userId, null);

        if (user == null) {
            return false;
        }

        if (user.getLogin().isAccountLocked()) {
            return false;
        }

        if (!isWithinInterval(user.getLogin().getLastLogin(), user.getTenant().getValue().getLoginIntervalDays())) {
            return false;
        }

        if (user.isExternalLoginRequired() && !isWithinInterval(user.getLogin().getLastExternalLogin(),
                                                                user.getTenant()
                                                                    .getValue()
                                                                    .getExternalLoginIntervalDays())) {
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

        long actualInterval = Duration.between(LocalDateTime.now(), dateTime).toDays();
        return actualInterval < requiredInterval;
    }

    private Set<String> computeRoles(UserAccount user, Tenant tenant, boolean isSystemTenant) {
        Set<String> roles = Sets.newTreeSet();
        roles.addAll(user.getPermissions().getPermissions());
        roles.addAll(tenant.getPermissions().getPermissions());
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

        UserAccount user = fetchAccount(userId, null);
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
        UserAccount account = fetchAccount(userId, null);
        if (account == null) {
            return "(unknown)";
        } else {
            return account.getLogin().getUsername();
        }
    }

    @Nonnull
    @Override
    protected String computeTenantname(@Nullable WebContext ctx, String tenantId) {
        Tenant tenant = fetchTenant(tenantId);
        if (tenant == null) {
            return "(unknown)";
        } else {
            return tenant.getName();
        }
    }

    @Nonnull
    @Override
    protected String computeLang(WebContext ctx, String userId) {
        return NLS.getDefaultLanguage();
    }
}
