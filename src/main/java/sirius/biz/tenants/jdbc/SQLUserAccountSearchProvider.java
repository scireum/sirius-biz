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
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.tenants.UserAccountSearchProvider;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Makes {@link SQLUserAccount user accounts} visible in the {@link sirius.biz.tycho.search.OpenSearchController}.
 * <p>
 * This additionally provides a secondary action to "select" / become a user if permitted.
 */
@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLUserAccountSearchProvider extends UserAccountSearchProvider<Long, SQLTenant, SQLUserAccount> {

    @Part
    private OMA oma;

    @Override
    protected Query<?, SQLUserAccount, ?> createBaseQuery(String query) {
        return oma.select(SQLUserAccount.class)
                  .fields(SQLEntity.ID,
                          UserAccount.TENANT.join(Tenant.TENANT_DATA.inner(TenantData.NAME)),
                          UserAccount.TENANT.join(Tenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER)),
                          UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.SALUTATION),
                          UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.TITLE),
                          UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.LASTNAME),
                          UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.FIRSTNAME),
                          UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.USERNAME))
                  .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.LASTNAME))
                  .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.FIRSTNAME))
                  .queryString(query,
                               QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.EMAIL)),
                               QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                .inner(LoginData.USERNAME)),
                               QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                .inner(PersonData.FIRSTNAME)),
                               QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                                .inner(PersonData.LASTNAME)));
    }
}
