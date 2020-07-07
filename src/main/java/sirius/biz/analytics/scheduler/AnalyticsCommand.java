/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.cluster.work.DistributedTasks;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.console.Command;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a maintenance command to execute analytical schedulers on demand and to also display their state.
 */
@Register
public class AnalyticsCommand implements Command {

    private static final String LINE_PATTERN = "%-35s %-30s %-10s";

    @Part
    private AnalyticalEngine analyticalEngine;

    @Part
    private DistributedTasks cluster;

    @Override
    public void execute(Output output, String... params) throws Exception {
        forceSchedulerIfRequested(output, params);
        outputSchedulerState(output);
    }

    private void outputSchedulerState(Output output) {
        output.apply(LINE_PATTERN, "NAME", "SCHEDULER QUEUE", "EXEC DATE");
        output.apply(LINE_PATTERN, "INTERVAL", "TASK QUEUE", "EXEC TIME");
        output.separator();

        Map<String, Integer> queueLengthCache = new HashMap<>();
        analyticalEngine.getActiveSchedulers().forEach(scheduler -> {
            output.apply(LINE_PATTERN,
                         scheduler.getName(),
                         fetchQueueName(scheduler.getExecutorForScheduling(), queueLengthCache),
                         analyticalEngine.getLastExecution(scheduler)
                                         .map(LocalDateTime::toLocalDate)
                                         .map(NLS::toMachineString)
                                         .orElse("-"));
            output.apply(LINE_PATTERN,
                         scheduler.getInterval(),
                         fetchQueueName(scheduler.getExecutorForTasks(), queueLengthCache),
                         analyticalEngine.getLastExecution(scheduler)
                                         .map(LocalDateTime::toLocalTime)
                                         .map(NLS::toMachineString)
                                         .orElse("-"));
            output.separator();
        });
        output.blankLine();
    }

    private void forceSchedulerIfRequested(Output output, String[] params) {
        if (params.length == 0) {
            output.apply("Use analytics <name> or analytics <name> <date> (like %s)",
                         NLS.toMachineString(LocalDate.now()));
            output.line("to forcefully execute a scheduler.");
            output.blankLine();
            return;
        }

        Optional<AnalyticsScheduler> analyticsScheduler = analyticalEngine.getActiveSchedulers()
                                                                          .stream()
                                                                          .filter(scheduler -> Strings.equalIgnoreCase(
                                                                                  params[0],
                                                                                  scheduler.getName()))
                                                                          .findFirst();
        if (!analyticsScheduler.isPresent()) {
            output.apply("Unknown scheduler: %s", params[0]);
        } else {
            output.apply("Executing scheduler '%s' manually...", params[0]);
            analyticalEngine.queueScheduler(analyticsScheduler.get(),
                                            true,
                                            params.length > 1 ?
                                            NLS.parseMachineString(LocalDate.class, params[1]) :
                                            null);
        }
        output.blankLine();
    }

    private String fetchQueueName(Class<? extends DistributedTaskExecutor> executor,
                                  Map<String, Integer> queueLengthCache) {
        String queueName = cluster.getQueueName(executor);
        return queueName + " (" + queueLengthCache.computeIfAbsent(queueName, cluster::getQueueLength) + ")";
    }

    @Override
    public String getDescription() {
        return "Permits to start analytics schedulers manually";
    }

    @Nonnull
    @Override
    public String getName() {
        return "analytics";
    }
}
