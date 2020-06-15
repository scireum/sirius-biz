/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.EntityExportJobFactory;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.UserAccountController;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;

/**
 * Provides an export for {@link SQLUserAccount user accounts}.
 */
@Register(classes = JobFactory.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLUserAccountExportJobFactory extends EntityExportJobFactory<SQLUserAccount, SmartQuery<SQLUserAccount>> {

    @Part
    private SQLTenants tenants;

    @Nonnull
    @Override
    public String getName() {
        return "export-sql-user-accounts";
    }

    @Override
    protected void checkPermissions() {
        UserAccountController.assertProperUserManagementPermission();
    }

    @Override
    protected Class<SQLUserAccount> getExportType() {
        return SQLUserAccount.class;
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return queryString.path().startsWith("/user-account");
    }
}
