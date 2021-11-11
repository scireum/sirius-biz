/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeList;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.biz.tenants.jdbc.DeleteSQLEntitiesTask;
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link CodeList code lists} of the given tenant.
 */
@Register(classes = DeleteTenantTask.class, framework = SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
public class DeleteSQLCodeListsTask extends DeleteSQLEntitiesTask<SQLCodeList> {

    @Override
    protected Class<SQLCodeList> getEntityClass() {
        return SQLCodeList.class;
    }

    @Override
    public int getPriority() {
        return 120;
    }
}
