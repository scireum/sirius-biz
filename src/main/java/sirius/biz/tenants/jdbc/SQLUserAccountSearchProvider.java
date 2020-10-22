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
import sirius.biz.tycho.search.OpenSearchProvider;
import sirius.biz.tycho.search.OpenSearchResult;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;

import java.util.function.Consumer;

@Register
public class SQLUserAccountSearchProvider implements OpenSearchProvider {

    @Part
    private OMA oma;

    @Override
    public String getLabel() {
        return "Anwender";
    }

    @Override
    public boolean ensureAccess() {
        return UserContext.getCurrentUser().hasPermission(UserAccountController.PERMISSION_MANAGE_USER_ACCOUNTS);
    }

    @Override
    public void query(String query, int maxResults, Consumer<OpenSearchResult> resultCollector) {
//        Wait.seconds(2);
        oma.select(SQLUserAccount.class)
           .queryString(query,
                        QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.EMAIL)),
                        QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                         .inner(LoginData.USERNAME)),
                        QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                         .inner(PersonData.FIRSTNAME)),
                        QueryField.contains(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                         .inner(PersonData.LASTNAME)))
           .limit(maxResults)
           .iterateAll(userAccount -> {
               resultCollector.accept(new OpenSearchResult(userAccount.getUserAccountData().getLogin().getUsername(),
                                                           userAccount.toString(),
                                                           "/user-account/" + userAccount.getIdAsString()));
           });
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
