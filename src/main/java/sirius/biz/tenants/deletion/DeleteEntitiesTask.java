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
 * <p>
 * Also see database specific implementations {@link DeleteMongoEntitiesTask} and {@link DeleteSQLEntitiesTask}.
 */
public abstract class DeleteEntitiesTask implements DeleteTenantTask {

    @Part
    protected Mixing mixing;

    @Override
    public void beforeExecution(ProcessContext processContext, Tenant<?> tenant, boolean simulate) {
        processContext.log(ProcessLog.info().withMessage(getMessage(tenant)));
    }

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

    protected abstract Query<?, ? extends TenantAware, ?> getQuery(Tenant<?> tenant);

    protected abstract Class<? extends TenantAware> getEntityClass();

    protected String getEntityName(long count) {
        if (count == 1) {
            return mixing.getDescriptor(getEntityClass()).getLabel();
        }
        return mixing.getDescriptor(getEntityClass()).getPluralLabel();
    }
}
