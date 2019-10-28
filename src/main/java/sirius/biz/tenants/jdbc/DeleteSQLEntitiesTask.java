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
import sirius.biz.tenants.deletion.DeleteEntitiesTask;
import sirius.biz.tenants.deletion.DeleteTenantJobFactory;
import sirius.biz.web.TenantAware;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;

/**
 * Deletes all entities of a given subclass of {@link SQLTenantAware} which belong to the given tenant.
 */
public abstract class DeleteSQLEntitiesTask extends DeleteEntitiesTask {

    @Part
    protected OMA oma;

    @Override
    public void execute(ProcessContext processContext, Tenant<?> tenant) throws Exception {
        getQuery(tenant).iterateAll(entity -> {
            Watch watch = Watch.start();
            oma.delete(entity);
            processContext.addTiming(DeleteTenantJobFactory.TIMING_DELETED_ITEMS, watch.elapsedMillis());
        });
    }

    @Override
    protected SmartQuery<? extends SQLTenantAware> getQuery(Tenant<?> tenant) {
        return oma.select(getEntityClass()).eq(TenantAware.TENANT, tenant);
    }

    /**
     * Defines the class of entities to be deleted.
     *
     * @return the class of the entities to be deleted
     */
    @Override
    protected abstract Class<? extends SQLTenantAware> getEntityClass();
}
