/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeList;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenant;
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
 * Deletes all {@link CodeList code lists} of the given tenant.
 */
@Register(framework = SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
public class DeleteSQLCodeListsTask implements DeleteTenantTask {

    @Part
    private OMA oma;

    private SmartQuery<SQLCodeList> getQuery(Tenant<?> tenant) {
        return oma.select(SQLCodeList.class).eq(TenantAware.TENANT, tenant);
    }

    @Override
    public void beforeExecution(ProcessContext process, Tenant<?> tenant, boolean simulate) {
        long codeListCount = getQuery(tenant).count();
        process.log(ProcessLog.info()
                              .withNLSKey("DeleteTenantTask.beforeExecution")
                              .withContext("count", codeListCount)
                              .withContext("name", NLS.get("CodeList.plural")));
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
        return 120;
    }
}
