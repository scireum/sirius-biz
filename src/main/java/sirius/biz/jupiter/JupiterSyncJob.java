/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Permits to manually force the update of the config and repository of all attached Jupiter instances.
 */
@Register(framework = Jupiter.FRAMEWORK_JUPITER)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class JupiterSyncJob extends SimpleBatchProcessJobFactory {

    private static final Parameter<Boolean> SYNC_CONFIG =
            new BooleanParameter("sync-config", "Synchronize Configuration").withDefaultTrue().build();
    private static final Parameter<Boolean> EXECUTE_DATA_PROVIDERS =
            new BooleanParameter("execute-providers", "Execute Data Providers").withDefaultTrue().build();
    private static final Parameter<Boolean> SYNC_REPO =
            new BooleanParameter("sync-rep", "Synchronize Repository").withDefaultTrue().build();

    @Part
    private JupiterSync jupiterSync;

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(SYNC_CONFIG);
        parameterCollector.accept(EXECUTE_DATA_PROVIDERS);
        parameterCollector.accept(SYNC_REPO);
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel();
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        jupiterSync.performSyncInProcess(process,
                                         process.require(SYNC_CONFIG),
                                         process.require(SYNC_REPO),
                                         process.require(EXECUTE_DATA_PROVIDERS));
    }

    @Nonnull
    @Override
    public String getName() {
        return "jupiter-sync";
    }

    @Override
    public String getCategory() {
        return StandardCategories.SYSTEM_ADMINISTRATION;
    }

    @Override
    public String getLabel() {
        return "Synchronize Jupiter Instances";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Pushes the configuration and repository contents into all attached Jupiter instances. "
               + "Note that this will also be done every night and during system startup.";
    }
}
