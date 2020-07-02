/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.mongo.MongoPerformanceData;
import sirius.biz.model.PersonData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountController;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.MongoPageHelper;
import sirius.db.mixing.query.QueryField;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.http.WebContext;

/**
 * Provides a GUI for managing user accounts.
 */
@Register(classes = {Controller.class, UserAccountController.class}, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoUserAccountController extends UserAccountController<String, MongoTenant, MongoUserAccount> {

    @Override
    protected BasePageHelper<MongoUserAccount, ?, ?, ?> getUsersAsPage(WebContext webContext) {
        MongoQuery<MongoUserAccount> baseQuery = mango.select(MongoUserAccount.class)
                                                      .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                             .inner(PersonData.LASTNAME))
                                                      .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                             .inner(PersonData.FIRSTNAME));

        MongoPageHelper<MongoUserAccount> pageHelper =
                MongoPageHelper.withQuery(tenants.forCurrentTenant(baseQuery)).withContext(webContext);
        pageHelper.withSearchFields(QueryField.startsWith(MongoUserAccount.SEARCH_PREFIXES));

        MongoPerformanceData.addFilterFacet(pageHelper);

        pageHelper.applyExtenders("/user-accounts");
        pageHelper.applyExtenders("/user-accounts/*");

        return pageHelper;
    }

    @Override
    protected BasePageHelper<MongoUserAccount, ?, ?, ?> getSelectableUsersAsPage() {
        MongoQuery<MongoUserAccount> baseQuery = mango.select(MongoUserAccount.class);

        if (!getUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_AFFILIATE)) {
            baseQuery = baseQuery.eq(UserAccount.TENANT, tenants.getRequiredTenant());
        }

        baseQuery.orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.LASTNAME))
                 .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.FIRSTNAME));

        MongoPageHelper<MongoUserAccount> pageHelper = MongoPageHelper.withQuery(baseQuery);
        pageHelper.withSearchFields(QueryField.startsWith(MongoUserAccount.SEARCH_PREFIXES));

        pageHelper.applyExtenders("/user-accounts/select");
        pageHelper.applyExtenders("/user-accounts/*");

        return pageHelper;
    }
}
