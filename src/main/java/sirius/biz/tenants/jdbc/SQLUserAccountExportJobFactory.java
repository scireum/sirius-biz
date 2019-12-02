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
import sirius.biz.tenants.UserAccountController;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Provides an export for {@link SQLUserAccount user accounts}.
 */
@Register(classes = JobFactory.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
@Permission(UserAccountController.PERMISSION_MANAGE_USER_ACCOUNTS)
public class SQLUserAccountExportJobFactory extends EntityExportJobFactory<SQLUserAccount, SmartQuery<SQLUserAccount>> {

    @Nonnull
    @Override
    public String getName() {
        return "export-sql-user-accounts";
    }

    @Override
    protected Class<SQLUserAccount> getExportType() {
        return SQLUserAccount.class;
    }

    @Override
    protected boolean hasPresetFor(Object targetObject) {
        return targetObject == SQLUserAccount.class;
    }
}
