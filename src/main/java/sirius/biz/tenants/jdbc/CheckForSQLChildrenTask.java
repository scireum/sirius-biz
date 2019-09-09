/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.biz.tenants.jdbc.SQLTenant;
import sirius.biz.tenants.jdbc.SQLTenants;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

/**
 * Makes sure the tenant which we are deleting does not have children. Or else the children need to be deleted before
 * the parent.
 */
@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class CheckForSQLChildrenTask implements DeleteTenantTask {

    @Part
    private SQLTenants tenants;

    @Override
    public void beforeExecution(ProcessContext process, Tenant<?> tenant, boolean simulate) {
        if (tenants.hasChildTenants(((SQLTenant) tenant).getId())) {
            throw Exceptions.createHandled().withNLSKey("CheckForChildrenTask.hasChildren").handle();
        }
    }

    @Override
    public void execute(ProcessContext process, Tenant<?> tenant) throws Exception {
        // we only need to check in this task in "beforeExecution" and have no actual work to do here.
    }

    @Override
    public int getPriority() {
        return 5;
    }
}
