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
 *
 * @param <E> the generic type of the entities being deleted
 */
public abstract class DeleteSQLEntitiesTask<E extends SQLTenantAware> extends DeleteEntitiesTask {

    @Part
    protected OMA oma;

    @Override
    public void execute(ProcessContext processContext, Tenant<?> tenant) throws Exception {
        getQuery(tenant).streamBlockwise().forEach(entity -> {
            Watch watch = Watch.start();
            beforeDelete(entity);
            oma.delete(entity);
            afterDelete(entity);
            processContext.addTiming(DeleteTenantJobFactory.TIMING_DELETED_ITEMS, watch.elapsedMillis());
        });
    }

    /**
     * Allows intercepting before an entity is deleted in the {@link #execute(ProcessContext, Tenant)} loop by
     * overwriting this method.
     *
     * @param entityToDelete the entity that will be deleted
     */
    protected void beforeDelete(E entityToDelete) {
        // No work to do by default here.
    }

    /**
     * Allows intercepting after an entity is deleted in the {@link #execute(ProcessContext, Tenant)} loop by
     * overwriting this method.
     *
     * @param entityToDelete the entity that was deleted
     */
    protected void afterDelete(E entityToDelete) {
        // No work to do by default here.
    }

    @Override
    protected SmartQuery<E> getQuery(Tenant<?> tenant) {
        return oma.select(getEntityClass()).eq(TenantAware.TENANT, tenant);
    }

    /**
     * Defines the class of entities to be deleted.
     *
     * @return the class of the entities to be deleted
     */
    @Override
    protected abstract Class<E> getEntityClass();
}
