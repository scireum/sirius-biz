/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.deletion;

import sirius.biz.mongo.MongoBizEntity;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenant;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.Mixing;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;

/**
 * Provides functionality to delete a list of MongoEntities of a tenant. Extending classes with basic use-cases only
 * need to define the class of the entity which should be deleted.
 */
public abstract class DeleteMongoEntitiesTask implements DeleteTenantTask {

    @Part
    protected Mango mango;

    @Part
    protected Mixing mixing;

    @Override
    public void beforeExecution(ProcessContext processContext, Tenant<?> tenant) {
        processContext.log(ProcessLog.info()
                                     .withNLSKey("DeleteTenantTask.beforeExecution")
                                     .withContext("count", getQuery(tenant).count())
                                     .withContext("name", getEntityName()));
    }

    @Override
    public void execute(ProcessContext processContext, Tenant<?> tenant) {
        getQuery(tenant).iterateAll(entity -> {
            Watch watch = Watch.start();
            mango.delete(entity);
            processContext.addTiming(DeleteTenantJobFactory.TIMING_DELETED_ITEMS, watch.elapsedMillis());
        });
    }

    protected MongoQuery<? extends MongoBizEntity> getQuery(Tenant<?> tenant) {
        return mango.select(getEntityClass()).eq(TenantAware.TENANT, tenant);
    }

    protected abstract Class<? extends MongoBizEntity> getEntityClass();

    protected String getEntityName() {
        return mixing.getDescriptor(getEntityClass()).getPluralLabel();
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
