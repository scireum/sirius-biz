/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link UserAccount user accounts} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class DeleteSQLUserAccountsTask extends DeleteSQLEntitiesTask<SQLUserAccount> {

    @Override
    protected Class<SQLUserAccount> getEntityClass() {
        return SQLUserAccount.class;
    }

    @Override
    public int getPriority() {
        return 200;
    }
}
