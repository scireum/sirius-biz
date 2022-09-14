/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.file.EntityExportJobFactory;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountController;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

/**
 * Provides an export for {@link SQLUserAccount user accounts}.
 */
@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLUserAccountExportJobFactory extends EntityExportJobFactory<SQLUserAccount, SmartQuery<SQLUserAccount>> {

    @Part
    private SQLTenants tenants;

    @Nonnull
    @Override
    public String getName() {
        return "export-sql-user-accounts";
    }

    @Override
    public String getCategory() {
        return StandardCategories.USERS_AND_TENANTS;
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Collections.singleton(UserAccountController.getUserManagementPermission());
    }

    @Override
    protected Class<SQLUserAccount> getExportType() {
        return SQLUserAccount.class;
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return targetObject instanceof Class<?> type && UserAccount.class.isAssignableFrom(type);
    }
}
