/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.deletion.DeleteTenantJobFactory;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.biz.web.TenantAware;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;

/**
 * Deletes all {@link UserAccount user accounts} of the given tenant.
 */
@Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class DeleteSQLUserAccountsTask implements DeleteTenantTask {

    @Part
    private OMA oma;

    private SmartQuery<SQLUserAccount> getQuery(Tenant<?> tenant) {
        return oma.select(SQLUserAccount.class).eq(TenantAware.TENANT, tenant);
    }

    @Override
    public void beforeExecution(ProcessContext process, Tenant<?> tenant, boolean simulate) {
        long userCount = getQuery(tenant).count();
        process.log(ProcessLog.info()
                              .withNLSKey("DeleteTenantTask.beforeExecution")
                              .withContext("count", userCount)
                              .withContext("name", NLS.get("UserAccount.plural")));
    }

    @Override
    public void execute(ProcessContext process, Tenant<?> tenant) throws Exception {
        getQuery(tenant).iterateAll(entry -> {
            Watch watch = Watch.start();
            oma.delete(entry);
            process.addTiming(DeleteTenantJobFactory.TIMING_DELETED_ITEMS, watch.elapsedMillis());
        });
    }

    @Override
    public int getPriority() {
        return 200;
    }
}
