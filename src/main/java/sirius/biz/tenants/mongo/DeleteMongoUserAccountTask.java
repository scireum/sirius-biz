/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.deletion.DeleteTenantJobFactory;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.biz.web.TenantAware;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Deletes all {@link UserAccount user accounts} of the given tenant.
 */
@Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class DeleteMongoUserAccountTask implements DeleteTenantTask {

    @Part
    private Mango mango;

    private MongoQuery<MongoUserAccount> getQuery(Tenant<?> tenant) {
        return mango.select(MongoUserAccount.class).eq(TenantAware.TENANT, tenant);
    }

    @Override
    public void beforeExecution(ProcessContext process, Tenant<?> tenant, boolean simulate) {
        long userCount = getQuery(tenant).count();
        process.log(ProcessLog.info()
                              .withNLSKey("DeleteUserAccountsTask.beforeExecution")
                              .withContext("count", userCount));
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
        return 200;
    }
}
