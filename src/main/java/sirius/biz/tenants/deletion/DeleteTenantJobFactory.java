/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.deletion;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.TenantParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccount;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mixing;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Deletes a given tenant and the related data.
 * <p>
 * Implement and register a {@link DeleteTenantTask} object to implement logic for deleting external tenant data,
 * like {@link UserAccount UserAccounts} for example.
 */
@Register(classes = JobFactory.class, framework = Tenants.FRAMEWORK_TENANTS)
@Permission(TenantController.PERMISSION_MANAGE_TENANTS)
public class DeleteTenantJobFactory extends SimpleBatchProcessJobFactory {

    /**
     * Can and should be used when invoking {@link ProcessContext#addTiming(String, long)} to report some progress when
     * deleting large lists of dependent objects.
     */
    public static final String TIMING_DELETED_ITEMS = "Deleted Items";

    protected static final TenantParameter TENANT_PARAMETER =
            new TenantParameter("tenant", "$Model.tenant").markRequired();

    protected static final BooleanParameter SIMULATION_PARAMETER =
            new BooleanParameter("simulate", "$DeleteTenantJobFactory.simulate");

    @Part
    private Tenants<?, ?, ?> tenants;

    @Part
    private Mixing mixing;

    @PriorityParts(DeleteTenantTask.class)
    private List<DeleteTenantTask> deletionTasks;

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        String name = TENANT_PARAMETER.require(context).getTenantData().getName();
        if (SIMULATION_PARAMETER.require(context)) {
            return NLS.fmtr("DeleteTenantJobFactory.title.simulate").set("name", name).format();
        } else {
            return NLS.fmtr("DeleteTenantJobFactory.title").set("name", name).format();
        }
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        Tenant<?> tenant = process.require(TENANT_PARAMETER);
        boolean simulate = process.require(DeleteTenantJobFactory.SIMULATION_PARAMETER);

        if (simulate) {
            process.log(ProcessLog.info().withNLSKey("DeleteTenantJobFactory.simulateInfo"));
        }

        for (DeleteTenantTask task : deletionTasks) {
            try {
                task.beforeExecution(process, tenant);
                if (!simulate) {
                    task.execute(process, tenant);
                }
            } catch (Exception e) {
                process.handle(Exceptions.handle()
                                         .to(Log.BACKGROUND)
                                         .error(e)
                                         .withSystemErrorMessage(
                                                 "An error occurred while executing the deletion task %s: %s (%s)",
                                                 task.getClass().getName())
                                         .handle());
                if (!simulate) {
                    process.log(ProcessLog.warn().withNLSKey("DeleteTenantJobFactory.aborting"));
                    return;
                }
            }
        }

        if (!simulate) {
            process.log(ProcessLog.info().withNLSKey("DeleteTenantJobFactory.deletingMainEntity"));
            mixing.getDescriptor(tenant.getClass()).getMapper().delete((BaseEntity<?>) tenant);
        }

        if (simulate) {
            process.log(ProcessLog.info().withNLSKey("DeleteTenantJobFactory.simulateInfo"));
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(TENANT_PARAMETER);
        parameterCollector.accept(SIMULATION_PARAMETER);
    }

    @Override
    public String getIcon() {
        return "fa-trash";
    }

    @Nonnull
    @Override
    public String getName() {
        return "delete-tenant";
    }
}
