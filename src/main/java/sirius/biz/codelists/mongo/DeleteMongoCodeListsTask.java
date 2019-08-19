/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeList;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.deletion.DeleteTenantJobFactory;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.biz.web.TenantAware;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link CodeList code lists} of the given tenant.
 */
@Register(framework = MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
public class DeleteMongoCodeListsTask implements DeleteTenantTask {

    @Part
    private Mango mango;

    private MongoQuery<MongoCodeList> getQuery(Tenant<?> tenant) {
        return mango.select(MongoCodeList.class).eq(TenantAware.TENANT, tenant);
    }

    @Override
    public void beforeExecution(ProcessContext process, Tenant<?> tenant, boolean simulate) {
        long codeListCount = getQuery(tenant).count();
        process.log(ProcessLog.info()
                              .withNLSKey("DeleteCodeListsTask.beforeExecution")
                              .withContext("count", codeListCount));
    }

    @Override
    public void execute(ProcessContext process, Tenant<?> tenant) throws Exception {
        getQuery(tenant).iterateAll(entry -> {
            Watch watch = Watch.start();
            mango.delete(entry);
            process.addTiming(DeleteTenantJobFactory.TIMING_DELETED_ITEMS, watch.elapsedMillis());
        });
    }

    @Override
    public int getPriority() {
        return 120;
    }
}
