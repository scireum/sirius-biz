/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.deletion.DeleteEntitiesTask;
import sirius.biz.tenants.deletion.DeleteTenantJobFactory;
import sirius.biz.tenants.mongo.MongoTenantAware;
import sirius.biz.web.TenantAware;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;

/**
 * Deletes all entities of a given subclass of {@link MongoTenantAware} which belong to the given tenant.
 */
public abstract class DeleteMongoEntitiesTask extends DeleteEntitiesTask {

    @Part
    protected Mango mango;

    @Override
    public void execute(ProcessContext processContext, Tenant<?> tenant) {
        getQuery(tenant).iterateAll(entity -> {
            Watch watch = Watch.start();
            mango.delete(entity);
            processContext.addTiming(DeleteTenantJobFactory.TIMING_DELETED_ITEMS, watch.elapsedMillis());
        });
    }

    @Override
    protected MongoQuery<? extends MongoTenantAware> getQuery(Tenant<?> tenant) {
        return mango.select(getEntityClass()).eq(TenantAware.TENANT, tenant);
    }

    /**
     * Defines the class of entities to be deleted.
     *
     * @return the class of the entities to be deleted
     */
    @Override
    protected abstract Class<? extends MongoTenantAware> getEntityClass();
}
