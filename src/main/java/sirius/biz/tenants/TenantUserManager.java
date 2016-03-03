/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import sirius.biz.model.LoginData;
import sirius.biz.web.BizController;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.extensions.Extension;
import sirius.kernel.health.Exceptions;
import sirius.mixing.OMA;
import sirius.web.http.WebContext;
import sirius.web.security.GenericUserManager;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserInfo;
import sirius.web.security.UserManager;
import sirius.web.security.UserManagerFactory;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Created by aha on 07.05.15.
 */
public class TenantUserManager extends GenericUserManager {

    public static final String PERMISSION_SYSTEM_TENANT = "flag-system-tenant";
    private final String systemTenant;
    private final String defaultSalt;
    private final boolean acceptApiTokens;
    private final boolean checkPasswordsCaseInsensitive;

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

    public static void flushCacheForUserAccount(UserAccount account) {
        rolesCache.remove(account.getIdAsString());
        userAccountCache.remove(account.getIdAsString());
        configCache.remove(account.getIdAsString());
    }

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
    }

    @Override
    protected UserInfo findUserByName(WebContext webContext, String user) {
        if (Strings.isEmpty(user)) {
            return null;
        }

        Optional<UserAccount> optionalAccount =
                oma.select(UserAccount.class).eq(UserAccount.LOGIN.inner(LoginData.USERNAME), user).one();

        if (optionalAccount.isPresent()) {
            if (optionalAccount.get().getLogin().isAccountLocked()) {
                throw Exceptions.createHandled().withNLSKey("LoginData.accountIsLocked").handle();
            }
            UserAccount account = optionalAccount.get();
            Tenant tenant = account.getTenant().getValue();

            UserAccount accountFromCache = userAccountCache.get(account.getIdAsString());
            if (accountFromCache == null || account.getVersion() > accountFromCache.getVersion()) {
                userAccountCache.put(account.getIdAsString(), account);
                rolesCache.remove(account.getIdAsString());
                configCache.remove(account.getIdAsString());
            } else {
                account = accountFromCache;
            }
            Tenant tenantFromCache = tenantsCache.get(tenant.getIdAsString());
            if (tenantFromCache == null || tenant.getVersion() > tenantFromCache.getVersion()) {
                tenantsCache.put(tenant.getIdAsString(), tenant);
                rolesCache.remove(account.getIdAsString());
                configCache.remove(account.getIdAsString());
            } else {
                tenant = tenantFromCache;
            }
            account.getTenant().setValue(tenant);

            return asUser(account);
        } else {
            return null;
        }
    }

    public UserInfo asUser(UserAccount account) {
        return new UserInfo(String.valueOf(account.getTenant().getId()),
                            account.getTenant().getValue().getName(),
                            account.getUniqueName(),
                            account.getLogin().getUsername(),
                            account.getEmail(),
                            "de",
                            computeRoles(null, String.valueOf(account.getUniqueName())),
                            ui -> getUserConfig(getScopeConfig(), ui),
                            this::getUserObject);
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
            account.getTrace().setSilent(true);
            account.getLogin().setNumberOfLogins(account.getLogin().getNumberOfLogins() + 1);
            account.getLogin().setLastLogin(LocalDateTime.now());
            oma.update(account);
        } catch (Throwable e) {
            Exceptions.handle(BizController.LOG, e);
        }
    }

    @Override
    protected Object getUserObject(UserInfo userInfo) {
        return getUserById(userInfo.getUserId());
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

    protected UserAccount getUserById(String id) {
        return userAccountCache.get(id, i -> (UserAccount) oma.resolve(i).orElse(null));
    }

    @Override
    protected boolean isUserStillValid(String userId) {
        UserAccount user = getUserById(userId);
        return user != null && !user.getLogin().isAccountLocked();
    }

    @Override
    protected Set<String> computeRoles(WebContext ctx, String userId) {
        Set<String> cachedRoles = rolesCache.get(userId);
        if (cachedRoles != null) {
            return cachedRoles;
        }
        Set<String> roles = Sets.newTreeSet();
        UserAccount user = getUserById(userId);
        if (user != null) {
            roles.addAll(user.getPermissions().getPermissions());
            roles.addAll(user.getTenant().getValue().getPermissions().getPermissions());
            if (Strings.areEqual(systemTenant, String.valueOf(user.getTenant().getId()))) {
                roles.add(PERMISSION_SYSTEM_TENANT);
            }
            roles.add(UserInfo.PERMISSION_LOGGED_IN);
            roles = transformRoles(roles, false);
        } else {
            return null;
        }

        rolesCache.put(userId, roles);
        return roles;
    }
}
