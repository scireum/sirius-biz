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
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountController;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.SQLPageHelper;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SmartQuery;
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
        return SQLPageHelper.withQuery(tenants.forCurrentTenant(baseQuery));
    }

    @Override
    protected BasePageHelper<SQLUserAccount, ?, ?, ?> getSelectableUsersAsPage() {
        SmartQuery<SQLUserAccount> baseQuery = oma.select(SQLUserAccount.class)
                                                  .eq(UserAccount.TENANT, tenants.getRequiredTenant())
                                                  .fields(SQLEntity.ID,
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

        return SQLPageHelper.withQuery(baseQuery);
    }
}
