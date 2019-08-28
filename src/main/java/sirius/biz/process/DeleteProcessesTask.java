/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.deletion.DeleteTenantJobFactory;
import sirius.biz.tenants.deletion.DeleteTenantTask;
import sirius.db.es.Elastic;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;

/**
 * Deletes all processes for a gicen tenant.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
public class DeleteProcessesTask implements DeleteTenantTask {

    @Part
    private Elastic elastic;

    @Override
    public void beforeExecution(ProcessContext processContext, Tenant<?> tenant, boolean simulate) {
        long count = elastic.select(Process.class).eq(Process.TENANT_ID, tenant.getIdAsString()).count();
        processContext.log(ProcessLog.info().withMessage(NLS.get("DeleteProcessesTask.deleteCount", (int) count)));
    }

    @Override
    public void execute(ProcessContext processContext, Tenant<?> tenant) throws Exception {
        elastic.select(Process.class).eq(Process.TENANT_ID, tenant.getIdAsString()).iterateAll(process -> {
            Watch watch = Watch.start();
            elastic.delete(process);
            processContext.addTiming(DeleteTenantJobFactory.TIMING_DELETED_ITEMS, watch.elapsedMillis());
        });
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
