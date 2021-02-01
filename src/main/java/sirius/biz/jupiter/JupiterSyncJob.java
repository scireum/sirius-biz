/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Permits to manually force the update of the config and repository of all attached Jupiter instances.
 */
@Register(framework = Jupiter.FRAMEWORK_JUPITER)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class JupiterSyncJob extends SimpleBatchProcessJobFactory {

    @Part
    private JupiterSync jupiterSync;

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // This job has no parameters.
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel();
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        jupiterSync.performSyncInProcess(process);
    }

    @Nonnull
    @Override
    public String getName() {
        return "jupiter-sync";
    }
}
