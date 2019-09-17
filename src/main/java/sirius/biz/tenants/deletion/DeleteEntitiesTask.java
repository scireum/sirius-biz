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
import sirius.biz.web.TenantAware;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

/**
 * Provides basic functionality to delete a list of {@link TenantAware} which belong to the given tenant.
 *
 * @see sirius.biz.tenants.jdbc.DeleteSQLEntitiesTask
 * @see sirius.biz.tenants.mongo.DeleteMongoEntitiesTask
 */
public abstract class DeleteEntitiesTask implements DeleteTenantTask {

    @Part
    protected Mixing mixing;

    @Override
    public void beforeExecution(ProcessContext processContext, Tenant<?> tenant, boolean simulate) {
        processContext.log(ProcessLog.info().withMessage(getMessage(tenant)));
    }

    /**
     * Computes the message to show in the log.
     * <p>
     * By default a generic message is built using the entity name provided by {@link #getEntityName(long)}.
     *
     * @param tenant the tenant being deleted
     * @return the message to show
     */
    protected String getMessage(Tenant<?> tenant) {
        final long count = getQuery(tenant).count();
        String entityName = getEntityName(count);
        if (count == 0) {
            return NLS.fmtr("DeleteTenantTask.beforeExecution.no").set("name", entityName).format();
        }
        if (count == 1) {
            return NLS.fmtr("DeleteTenantTask.beforeExecution.one").set("name", entityName).format();
        }
        return NLS.fmtr("DeleteTenantTask.beforeExecution.many").set("count", count).set("name", entityName).format();
    }

    /**
     * Constructs the query which determines which entities should be deleted.
     *
     * @param tenant the tenant which is being deleted
     * @return the query which matches all entities to be deleted
     */
    protected abstract Query<?, ? extends TenantAware, ?> getQuery(Tenant<?> tenant);

    /**
     * Returns the entity class to delete.
     *
     * @return the type of entities being deleted by this task
     */
    protected abstract Class<? extends TenantAware> getEntityClass();

    /**
     * Determines the entity name to use in the log message.
     *
     * @param count the number of entities being deleted to properly differentiate singular and plural forms
     * @return the name to use for the entities based on the given count
     */
    protected String getEntityName(long count) {
        if (count == 1) {
            return mixing.getDescriptor(getEntityClass()).getLabel();
        }
        return mixing.getDescriptor(getEntityClass()).getPluralLabel();
    }
}
