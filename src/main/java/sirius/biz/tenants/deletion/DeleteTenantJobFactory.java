/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.deletion;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.jobs.params.TenantParameter;
import sirius.biz.process.PersistencePeriod;
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
import sirius.web.http.QueryString;
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
@Register(framework = Tenants.FRAMEWORK_TENANTS)
@Permission(TenantController.PERMISSION_MANAGE_TENANTS)
public class DeleteTenantJobFactory extends SimpleBatchProcessJobFactory {

    /**
     * Can and should be used when invoking {@link ProcessContext#addTiming(String, long)} to report some progress when
     * deleting large lists of dependent objects.
     */
    public static final String TIMING_DELETED_ITEMS = "Deleted Items";

    protected static final Parameter<Tenant<?>> TENANT_PARAMETER =
            new TenantParameter("tenant", "$Model.tenant").markRequired().build();

    protected static final Parameter<String> TENANT_NAME =
            new StringParameter("tenantName", "$DeleteTenantJobFactory.tenantName").withDescription(
                    "$DeleteTenantJobFactory.tenantName.help").markRequired().build();

    protected static final Parameter<Boolean> SIMULATE_PARAMETER =
            new BooleanParameter("simulate", "$DeleteTenantJobFactory.simulate").withDescription(
                    "$DeleteTenantJobFactory.simulate.help").withDefaultTrue().build();

    @Part
    private Tenants<?, ?, ?> tenants;

    @Part
    private Mixing mixing;

    @PriorityParts(DeleteTenantTask.class)
    private List<DeleteTenantTask> deletionTasks;

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        String name = TENANT_PARAMETER.require(context).getTenantData().getName();
        if (Boolean.FALSE.equals(SIMULATE_PARAMETER.require(context))) {
            return NLS.fmtr("DeleteTenantJobFactory.title").set("name", name).format();
        } else {
            return NLS.fmtr("DeleteTenantJobFactory.title.simulate").set("name", name).format();
        }
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        Tenant<?> tenant = process.require(TENANT_PARAMETER);
        boolean shouldExecute = !process.require(DeleteTenantJobFactory.SIMULATE_PARAMETER);
        String providedTenantName = process.require(TENANT_NAME);

        if (!providedTenantName.equals(tenant.getTenantData().getName())) {
            throw Exceptions.createHandled().withNLSKey("DeleteTenantJobFactory.tenantNameMismatch").handle();
        }

        if (!shouldExecute) {
            process.log(ProcessLog.info().withNLSKey("DeleteTenantJobFactory.simulateInfo"));
        }

        for (DeleteTenantTask task : deletionTasks) {
            try {
                task.beforeExecution(process, tenant, shouldExecute);
                if (shouldExecute) {
                    task.execute(process, tenant);
                }
            } catch (Exception exception) {
                process.handle(Exceptions.handle()
                                         .to(Log.BACKGROUND)
                                         .error(exception)
                                         .withSystemErrorMessage(
                                                 "An error occurred while executing the deletion task %s: %s (%s)",
                                                 task.getClass().getName())
                                         .handle());
                if (shouldExecute) {
                    process.log(ProcessLog.warn().withNLSKey("DeleteTenantJobFactory.aborting"));
                    return;
                }
            }
        }

        if (shouldExecute) {
            process.log(ProcessLog.info().withNLSKey("DeleteTenantJobFactory.deletingMainEntity"));
            mixing.getDescriptor(tenant.getClass()).getMapper().delete((BaseEntity<?>) tenant);
        } else {
            process.log(ProcessLog.info().withNLSKey("DeleteTenantJobFactory.simulateInfo"));
        }
    }

    @Override
    protected boolean hasPresetFor(@Nonnull QueryString queryString, Object targetObject) {
        return targetObject instanceof Tenant;
    }

    @Override
    protected void computePresetFor(@Nonnull QueryString queryString,
                                    Object targetObject,
                                    @Nonnull Map<String, Object> preset) {
        preset.put(TENANT_PARAMETER.getName(), ((Tenant<?>) targetObject).getIdAsString());
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(TENANT_PARAMETER);
        parameterCollector.accept(TENANT_NAME);
        parameterCollector.accept(SIMULATE_PARAMETER);
    }

    @Override
    public String getIcon() {
        return "fa-solid fa-trash";
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.TEN_YEARS;
    }

    @Nonnull
    @Override
    public String getName() {
        return "delete-tenant";
    }

    @Override
    public int getPriority() {
        return 9210;
    }

    @Override
    public String getCategory() {
        return StandardCategories.USERS_AND_TENANTS;
    }
}
