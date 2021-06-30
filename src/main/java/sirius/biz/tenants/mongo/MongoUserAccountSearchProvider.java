/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.model.PersonData;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.tenants.UserAccountSearchProvider;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.QueryField;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Makes {@link MongoUserAccount user accounts} visible in the {@link sirius.biz.tycho.search.OpenSearchController}.
 * <p>
 * This additionally provides a secondary action to "select" / become a user if permitted.
 */
@Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoUserAccountSearchProvider extends UserAccountSearchProvider<String, MongoTenant, MongoUserAccount> {

    @Part
    private Mango mango;

    @Override
    protected Query<?, MongoUserAccount, ?> createBaseQuery(String query) {
        return mango.select(MongoUserAccount.class)
                    .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.LASTNAME))
                    .orderAsc(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.FIRSTNAME))
                    .queryString(query, QueryField.startsWith(MongoUserAccount.SEARCH_PREFIXES));
    }
}
