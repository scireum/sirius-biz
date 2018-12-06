/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.model.LoginData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.web.BizController;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLQuery;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserInfo;
import sirius.web.security.UserManager;
import sirius.web.security.UserManagerFactory;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Provides a {@link UserManager} for {@link Tenant} and {@link UserAccount}.
 * <p>
 * The user managed can be installed by setting the <tt>manager</tt> property of the scope to <tt>tenants</tt>
 * in the system config.
 * <p>
 * This is the default user manager for the default scope in <tt>sirius-biz</tt>.
 */
public class SQLTenantUserManager extends TenantUserManager<Long, SQLTenant, SQLUserAccount> {

    @Part
    private static OMA oma;

    /**
     * Creates a new user manager for the given scope and configuration.
     */
    @Register(name = "tenants", framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
    public static class Factory implements UserManagerFactory {

        @Nonnull
        @Override
        public UserManager createManager(@Nonnull ScopeInfo scope, @Nonnull Extension config) {
            return new SQLTenantUserManager(scope, config);
        }
    }

    protected SQLTenantUserManager(ScopeInfo scope, Extension config) {
        super(scope, config);
    }

    @Override
    protected SQLTenant loadTenant(String tenantId) {
        return oma.find(SQLTenant.class, tenantId).orElse(null);
    }

    @Override
    protected Optional<SQLUserAccount> loadAccountByName(String user) {
        return oma.select(SQLUserAccount.class)
                  .eq(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.USERNAME), user.toLowerCase())
                  .one();
    }

    @Override
    protected SQLUserAccount loadAccount(@Nonnull String accountId) {
        return (SQLUserAccount) oma.resolve(accountId).orElse(null);
    }

    @Override
    protected SQLTenant loadTenantOfAccount(SQLUserAccount account) {
        return oma.find(SQLTenant.class, account.getTenant().getId())
                  .orElseThrow(() -> new IllegalStateException(Strings.apply("Tenant %s for UserAccount %s vanished!",
                                                                             account.getTenant().getId(),
                                                                             account.getId())));
    }

    @Override
    protected Class<SQLUserAccount> getUserAccountType() {
        return SQLUserAccount.class;
    }

    @Override
    protected void recordLogin(UserInfo user, boolean external) {
        try {
            SQLUserAccount account = getUserObject(user);
            EntityDescriptor ed = mixing.getDescriptor(getUserAccountType());

            String numberOfLoginsField = ed.getProperty(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                     .inner(LoginData.NUMBER_OF_LOGINS))
                                           .getPropertyName();
            String lastLoginField = ed.getProperty(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                .inner(LoginData.LAST_LOGIN))
                                      .getPropertyName();

            if (external) {
                String lastExternalLoginField =
                        ed.getProperty(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                    .inner(LoginData.LAST_EXTERNAL_LOGIN))
                          .getPropertyName();
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
}
