/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.tenants.jdbc.SQLUserAccount;
import sirius.biz.tycho.QuickAction;
import sirius.biz.tycho.search.OpenSearchProvider;
import sirius.biz.tycho.search.OpenSearchResult;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.query.Query;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Provides the base implementation to make implementations of {@link UserAccount} visible in the
 * {@link sirius.biz.tycho.search.OpenSearchController}.
 *
 * @param <I> the type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
public abstract class UserAccountSearchProvider<I extends Serializable, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        implements OpenSearchProvider {

    @Part
    private Tenants<I, T, U> tenants;

    @Override
    public String getLabel() {
        return NLS.get("UserAccount.plural");
    }

    @Nullable
    @Override
    public String getUrl() {
        UserInfo currentUser = UserContext.getCurrentUser();
        if (currentUser.hasPermission(UserAccountController.getUserManagementPermission())) {
            return "/user-accounts";
        } else {
            return "/user-accounts/select";
        }
    }

    @Override
    public boolean ensureAccess() {
        UserInfo currentUser = UserContext.getCurrentUser();
        return currentUser.hasPermission(TenantUserManager.PERMISSION_SELECT_USER_ACCOUNT) || currentUser.hasPermission(
                UserAccountController.getUserManagementPermission());
    }

    @Override
    public void query(String query, int maxResults, Consumer<OpenSearchResult> resultCollector) {
        UserInfo currentUser = UserContext.getCurrentUser();
        Tenant<?> currentTenant = tenants.getRequiredTenant();

        Query<?, U, ?> userAccountQuery = createBaseQuery(query);
        userAccountQuery.limit(maxResults);

        if (!currentUser.hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_AFFILIATE)) {
            // If we're NOT part of the system tenant, we only may search within our own tenant...
            userAccountQuery.eq(SQLUserAccount.TENANT, currentTenant);
        } else if (!currentUser.hasPermission(UserAccountController.getUserManagementPermission())) {
            // If we're the system tenant but have no user mangement permission there, we may not see
            // and edit or select our own users...
            userAccountQuery.ne(SQLUserAccount.TENANT, currentTenant);
        }

        userAccountQuery.iterateAll(userAccount -> {
            OpenSearchResult openSearchResult =
                    new OpenSearchResult().withLabel(userAccount.getUserAccountData().getLogin().getUsername());
            if (Objects.equals(currentTenant.getIdAsString(), userAccount.getTenant().getIdAsString())) {
                openSearchResult.withDescription(userAccount.toString())
                                .withURL("/user-account/" + userAccount.getIdAsString());
            } else {
                openSearchResult.withDescription(userAccount + " (" + userAccount.getTenant()
                                                                                 .fetchValue()
                                                                                 .toString() + ")")
                                .withURL("/tenants/select/"
                                         + userAccount.getTenant().getIdAsString()
                                         + "?goto="
                                         + Strings.urlEncode("/user-account/" + userAccount.getIdAsString()));
            }
            if (currentUser.hasPermission(TenantUserManager.PERMISSION_SELECT_USER_ACCOUNT)) {
                openSearchResult.withQuickAction(new QuickAction().withLabel(NLS.get("TenantController.select"))
                                                                  .withIcon("fa fa-user")
                                                                  .withUrl("/user-accounts/select/"
                                                                           + userAccount.getIdAsString()));
            }
            resultCollector.accept(openSearchResult);
        });
    }

    protected abstract Query<?, U, ?> createBaseQuery(String query);

    @Override
    public int getPriority() {
        return 100;
    }
}
