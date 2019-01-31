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
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.web.BizController;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLQuery;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
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

/**
 * Provides a {@link UserManager} for {@link Tenant} and {@link UserAccount}.
 * <p>
 * The user manager can be installed by setting the <tt>manager</tt> property of the scope to <tt>tenants</tt>
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
    protected void recordLogin(UserInfo user, boolean external) {
        try {
            SQLUserAccount account = getUserObject(user);
            EntityDescriptor ed = mixing.getDescriptor(getUserClass());

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
