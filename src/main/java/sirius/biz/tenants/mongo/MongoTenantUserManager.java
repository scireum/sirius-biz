/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.model.LoginData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.web.BizController;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
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
public class MongoTenantUserManager extends TenantUserManager<String, MongoTenant, MongoUserAccount> {

    @Part
    private static Mongo mongo;

    /**
     * Creates a new user manager for the given scope and configuration.
     */
    @Register(name = "tenants", framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
    public static class Factory implements UserManagerFactory {

        @Nonnull
        @Override
        public UserManager createManager(@Nonnull ScopeInfo scope, @Nonnull Extension config) {
            return new MongoTenantUserManager(scope, config);
        }
    }

    protected MongoTenantUserManager(ScopeInfo scope, Extension config) {
        super(scope, config);
    }

    @Override
    protected void recordLogin(UserInfo user, boolean external) {
        try {
            MongoUserAccount account = getUserObject(user);
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
                mongo.update()
                     .where(MongoEntity.ID, account.getId())
                     .inc(numberOfLoginsField, 1)
                     .set(lastExternalLoginField, LocalDateTime.now())
                     .set(lastLoginField, LocalDateTime.now())
                     .executeFor(ed.getRelationName());
            } else {
                mongo.update()
                     .where(MongoEntity.ID, account.getId())
                     .inc(numberOfLoginsField, 1)
                     .set(lastLoginField, LocalDateTime.now())
                     .executeFor(ed.getRelationName());
            }
        } catch (Exception e) {
            Exceptions.handle(BizController.LOG, e);
        }
    }
}
