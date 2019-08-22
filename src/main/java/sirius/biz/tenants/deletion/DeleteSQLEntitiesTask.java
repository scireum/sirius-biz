/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.deletion;

import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.jdbc.SQLTenantAware;
import sirius.biz.web.TenantAware;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;

/**
 * Deletes all entities of a given subclass of {@link SQLTenantAware} which belong to the given tenant.
 */
public abstract class DeleteSQLEntitiesTask implements DeleteTenantTask {

    @Part
    protected OMA oma;

    @Part
    protected Mixing mixing;

    @Override
    public void beforeExecution(ProcessContext processContext, Tenant<?> tenant, boolean simulate) {
        processContext.log(ProcessLog.info()
                                     .withNLSKey("DeleteTenantTask.beforeExecution")
                                     .withContext("count", getQuery(tenant).count())
                                     .withContext("name", getEntityName()));
    }

    @Override
    public void execute(ProcessContext processContext, Tenant<?> tenant) throws Exception {
        getQuery(tenant).iterateAll(entity -> {
            Watch watch = Watch.start();
            oma.delete(entity);
            processContext.addTiming(DeleteTenantJobFactory.TIMING_DELETED_ITEMS, watch.elapsedMillis());
        });
    }

    private SmartQuery<? extends SQLTenantAware> getQuery(Tenant<?> tenant) {
        return oma.select(getEntityClass()).eq(TenantAware.TENANT, tenant);
    }

    /**
     * Defines the class of entities to be deleted.
     *
     * @return the class of the entities to be deleted
     */
    protected abstract Class<? extends SQLTenantAware> getEntityClass();

    protected String getEntityName() {
        return mixing.getDescriptor(getEntityClass()).getPluralLabel();
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
