/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler;

import sirius.biz.jobs.Jobs;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;

/**
 * Responsible for executing all {@link SchedulerEntry scheduler entries} of all {@link SchedulerEntryProvider providers}.
 */
@Register(framework = Jobs.FRAMEWORK_JOBS)
public class JobSchedulerLoop extends BackgroundLoop {

    @Part
    private Jobs jobs;

    @Part
    private ScheduledEntryExecution entryExecution;

    @Parts(SchedulerEntryProvider.class)
    private PartCollection<SchedulerEntryProvider<?>> providers;

    @Nonnull
    @Override
    public String getName() {
        return "job-scheduler";
    }

    @Override
    public double maxCallFrequency() {
        return 1d / 50;
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        int startedJobs = 0;
        for (SchedulerEntryProvider<?> provider : providers) {
            startedJobs += executeEntriesOfProvider(now, provider);
        }

        if (startedJobs == 0) {
            return null;
        }

        return "Started Jobs: " + startedJobs;
    }

    private <J extends SchedulerEntry> int executeEntriesOfProvider(LocalDateTime now,
                                                                    SchedulerEntryProvider<J> provider) {
        int startedJobs = 0;
        for (J entry : provider.getActiveScheduledJobs()) {
            try {
                if (entry.getSchedulerData().shouldRun(now)) {
                    entry = provider.fetchFullInformation(entry);
                    entryExecution.executeJob(provider, entry, now);
                    startedJobs++;
                }
            } catch (Exception exception) {
                Exceptions.handle()
                          .to(Log.BACKGROUND)
                          .error(exception)
                          .withSystemErrorMessage(
                                  "An error occurred while checking a scheduled task of %s: %s - %s (%s)",
                                  provider.getClass().getSimpleName(),
                                  entry)
                          .handle();
            }
        }

        return startedJobs;
    }
}
