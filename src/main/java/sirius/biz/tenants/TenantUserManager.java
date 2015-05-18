/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import com.google.common.collect.Sets;
import sirius.biz.model.LoginData;
import sirius.biz.web.BizController;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.extensions.Extension;
import sirius.kernel.health.Exceptions;
import sirius.mixing.OMA;
import sirius.web.http.WebContext;
import sirius.web.security.*;

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

    /**
     * Creates a new user manager for the given scope and configuration.
     */
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

    protected TenantUserManager(ScopeInfo scope, Extension config) {
        super(scope, config);
        this.sessionStorage = SESSION_STORAGE_TYPE_CLIENT;
        this.systemTenant = config.get("system-tenant").asString();
    }

    @Override
    protected UserInfo findUserByName(WebContext webContext, String user) {
        if (Strings.isEmpty(user)) {
            return null;
        }
        Optional<UserAccount> optionalAccount = oma.select(UserAccount.class)
                                                   .eq(UserAccount.LOGIN.inner(LoginData.USERNAME), user)
                                                   .one();
        if (optionalAccount.isPresent()) {
            if (optionalAccount.get().getLogin().isAccountLocked()) {
                throw Exceptions.createHandled().withNLSKey("LoginData.accountIsLocked").handle();
            }
            UserAccount account = optionalAccount.get();
            userAccountCache.put(account.getIdAsString(), account);
            rolesCache.remove(account.getIdAsString());
            return new UserInfo(String.valueOf(account.getTenant().getId()),
                                account.getTenant().getValue().getName(),
                                String.valueOf(account.getId()),
                                account.getLogin().getUsername(),
                                account.getEmail(),
                                "de",
                                computeRoles(null, String.valueOf(account.getId())),
                                this::getUserObject);
        } else {
            return null;
        }
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
        if (LoginData.hashPassword(loginData.getSalt(), password).equals(loginData.getPasswordHash())) {
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

    protected UserAccount getUserById(String id) {
        return userAccountCache.get(id, i -> oma.findOrFail(UserAccount.class, i));
    }

    @Override
    protected Set<String> computeRoles(WebContext ctx, String userId) {
        Set<String> cachedRoles = rolesCache.get(userId);
        if (cachedRoles != null) {
            return cachedRoles;
        }
        Set<String> roles = Sets.newTreeSet();
        UserAccount user = getUserById(userId);
        if (user != null && !user.getLogin().isAccountLocked()) {
            roles.addAll(user.getPermissions().getPermissions());
            roles.addAll(user.getTenant().getValue().getPermissions().getPermissions());
            if (Strings.areEqual(systemTenant, String.valueOf(user.getTenant().getId()))) {
                roles.add(PERMISSION_SYSTEM_TENANT);
            }
            roles.add(UserInfo.PERMISSION_LOGGED_IN);
            roles = transformRoles(roles, false);
        }
        rolesCache.put(userId, roles);
        return roles;
    }
}
