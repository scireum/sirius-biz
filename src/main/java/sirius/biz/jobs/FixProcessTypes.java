/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.Process;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.Elastic;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Provides a job which fixes process types to now used getName() instead of the NLS-key as processType.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class FixProcessTypes extends SimpleBatchProcessJobFactory {

    @PriorityParts(JobFactory.class)
    private List<JobFactory> factories;

    @Part
    private Elastic elastic;

    private static final Parameter<Boolean> SIMULATION =
            new BooleanParameter("simulation", "Simulation").withDescription(
                                                                    "Simulation mode will only output which keys are missing and how many processes would have their type changed.")
                                                            .withDefaultTrue()
                                                            .build();

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(SIMULATION);
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        boolean simulation = process.require(SIMULATION);

        Map<String, String> jobTypesAndKeys = factories.stream()
                                                       .filter(JobFactory::canStartInBackground)
                                                       .distinct()
                                                       .collect(HashMap::new,
                                                                (map, job) -> map.put(job.getName(),
                                                                                      job.getClass().getSimpleName()
                                                                                      + ".label"),
                                                                HashMap::putAll);

        process.log(ProcessLog.info().withMessage(Strings.apply("Found %s jobs", jobTypesAndKeys.size())));
        jobTypesAndKeys.forEach((type, nlsKey) -> {
            AtomicInteger counter = new AtomicInteger();
            if (!NLS.exists(nlsKey, null)) {
                process.log(ProcessLog.warn()
                                      .withMessage(Strings.apply("No NLS key found for type %s (%s)", type, nlsKey)));
            }

            elastic.select(Process.class).eq(Process.PROCESS_TYPE, nlsKey).iterateAll(executedProcess -> {
                if (!simulation) {
                    executedProcess.setProcessType(type);
                    elastic.update(executedProcess);
                }
                counter.getAndIncrement();
            });

            if (counter.get() > 0) {
                if (simulation) {
                    process.log(ProcessLog.info()
                                          .withMessage(Strings.apply(
                                                  "Would update the processType on %s Processes from '%s' to '%s'",
                                                  counter.get(),
                                                  nlsKey,
                                                  type)));
                } else {
                    process.log(ProcessLog.success()
                                          .withMessage(Strings.apply(
                                                  "Updated the processType on %s Processes from '%s' to '%s'",
                                                  counter.get(),
                                                  nlsKey,
                                                  type)));
                }
            }
        });
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return "Fix process types";
    }

    @Nonnull
    @Override
    public String getName() {
        return "fix-process-types";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Fixes process-types to now use getName() instead of the NLS-key as processType.";
    }

    @Override
    public String getLabel() {
        return "Fix process types";
    }

    @Override
    public String getCategory() {
        return StandardCategories.SYSTEM_ADMINISTRATION;
    }
}
