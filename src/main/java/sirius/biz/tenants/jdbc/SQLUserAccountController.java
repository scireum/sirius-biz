/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.model.LoginData;
import sirius.biz.model.PersonData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountController;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.SQLPageHelper;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SmartQuery;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;

/**
 * Provides a GUI for managing user accounts.
 */
@Register(classes = {Controller.class, UserAccountController.class}, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLUserAccountController extends UserAccountController<Long, SQLTenant, SQLUserAccount> {

    @Override
    protected BasePageHelper<SQLUserAccount, ?, ?, ?> getUsersAsPage() {
        SmartQuery<SQLUserAccount> baseQuery = oma.select(SQLUserAccount.class)
                                                  .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                         .inner(PersonData.LASTNAME))
                                                  .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                         .inner(PersonData.FIRSTNAME));

        SQLPageHelper<SQLUserAccount> pageHelper = SQLPageHelper.withQuery(tenants.forCurrentTenant(baseQuery));
        pageHelper.withSearchFields(QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.EMAIL)),
                                    QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                     .inner(LoginData.USERNAME)),
                                    QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                     .inner(PersonData.FIRSTNAME)),
                                    QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                     .inner(PersonData.LASTNAME)));

        return pageHelper;
    }

    @Override
    protected BasePageHelper<SQLUserAccount, ?, ?, ?> getSelectableUsersAsPage() {
        SmartQuery<SQLUserAccount> baseQuery = oma.select(SQLUserAccount.class)
                                                  .fields(SQLEntity.ID,
                                                          UserAccount.TENANT.join(Tenant.TENANT_DATA.inner(TenantData.NAME)),
                                                          UserAccount.TENANT.join(Tenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER)),
                                                          UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                       .inner(PersonData.LASTNAME),
                                                          UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                       .inner(PersonData.FIRSTNAME),
                                                          UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                       .inner(LoginData.USERNAME))
                                                  .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                         .inner(PersonData.LASTNAME))
                                                  .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                         .inner(PersonData.FIRSTNAME));

        if (!hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            baseQuery = baseQuery.eq(UserAccount.TENANT, tenants.getRequiredTenant());
        }

        SQLPageHelper<SQLUserAccount> pageHelper = SQLPageHelper.withQuery(baseQuery);
        pageHelper.withSearchFields(QueryField.contains(UserAccount.TENANT.join(Tenant.TENANT_DATA.inner(TenantData.NAME))),
                                    QueryField.contains(UserAccount.TENANT.join(Tenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER))),
                                    QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                     .inner(PersonData.LASTNAME)),
                                    QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                     .inner(PersonData.FIRSTNAME)),
                                    QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                     .inner(LoginData.USERNAME)),
                                    QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.EMAIL)));

        return pageHelper;
    }
}
